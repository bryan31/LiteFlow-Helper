package com.yomahub.liteflowhelper.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * LiteFlow EL 表达式格式化器（纯 Java，不依赖 IntelliJ 平台）。
 * <p>
 * 智能混合策略：表达式平铺宽度不超过行宽预算时保持单行，否则按嵌套层级展开多行。
 * 不增删任何 token，仅重排 token 之间的空白；括号不匹配时返回失败。
 * </p>
 */
public final class LiteFlowElFormatter {

    private LiteFlowElFormatter() {
    }

    public static @NotNull FormatResult format(@NotNull String elText, @NotNull ElFormatOptions options) {
        // CDATA 写法的 chain 值文本（<![CDATA[...]]>）含 CDATA 起止标记，分词会把标记当标点重排，
        // 写回后 XML 无法解析；此处直接拒收，由 IDE 层走 failure 提示路径
        int contentStart = 0;
        while (contentStart < elText.length() && elText.charAt(contentStart) <= ' ') {
            contentStart++;
        }
        if (elText.startsWith("<![CDATA", contentStart)) {
            return FormatResult.failure("暂不支持 CDATA 包裹的 EL");
        }
        List<LiteFlowElToken> tokens = LiteFlowElLexer.tokenize(elText);
        if (!hasNonComment(tokens, 0)) {
            return FormatResult.failure("EL 为空");
        }
        int[] match = matchParens(tokens);
        if (match == null) {
            return FormatResult.failure("EL 表达式括号不匹配");
        }
        StringBuilder out = new StringBuilder(elText.length() + 32);
        // 顶层按括号深度 0 的 ; 切分语句，语句各自独占一行
        int depth = 0;
        int stmtStart = 0;
        for (int i = 0; i < tokens.size(); i++) {
            LiteFlowElToken t = tokens.get(i);
            if (t.type != LiteFlowElToken.Type.PUNCT) {
                continue;
            }
            if ("(".equals(t.text)) {
                depth++;
            } else if (")".equals(t.text)) {
                depth--;
            } else if (";".equals(t.text) && depth == 0) {
                emitStatement(out, tokens, match, stmtStart, i, options);
                out.append(';');
                stmtStart = i + 1;
                // 后面还有实质内容才换行；只剩注释时由 emitStatement 粘连到当前行
                if (hasNonComment(tokens, stmtStart)) {
                    newlineIndent(out, options.baseIndent);
                }
            }
        }
        emitStatement(out, tokens, match, stmtStart, tokens.size(), options);
        return FormatResult.success(out.toString());
    }

    /** 输出一条语句：[子变量前缀] 表达式；区间为空或为纯注释（尾随注释粘连）时特殊处理。 */
    private static void emitStatement(@NotNull StringBuilder out, @NotNull List<LiteFlowElToken> tokens,
                                      int[] match, int start, int end, @NotNull ElFormatOptions opt) {
        if (start >= end) {
            return;
        }
        // 纯注释区间（语句结尾分号之后的注释）：粘连到当前行
        boolean allComments = true;
        for (int k = start; k < end; k++) {
            if (tokens.get(k).type != LiteFlowElToken.Type.COMMENT) {
                allComments = false;
                break;
            }
        }
        if (allComments) {
            for (int k = start; k < end; k++) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(tokens.get(k).text);
            }
            return;
        }
        // 子变量定义前缀：IDENT 紧跟 =
        int exprStart = start;
        if (end - start >= 2
                && tokens.get(start).type == LiteFlowElToken.Type.IDENT
                && tokens.get(start + 1).type == LiteFlowElToken.Type.PUNCT
                && "=".equals(tokens.get(start + 1).text)) {
            out.append(tokens.get(start).text).append(" = ");
            exprStart = start + 2;
        }
        // 表达式本体：整体平铺或按层级展开
        emitExpr(out, tokens, match, exprStart, end, 0, opt);
    }

    /**
     * 输出 [i, j) 区间的一个表达式：头部（标识符/字面量 + 可选分组）+ 点号续写段序列。
     * 整条链能平铺则全部同行；否则逐段处理，后缀能平铺则粘连，不能则断开当前段内部。
     */
    private static void emitExpr(@NotNull StringBuilder out, @NotNull List<LiteFlowElToken> tokens,
                                 int[] match, int i, int j, int level, @NotNull ElFormatOptions opt) {
        if (i >= j) {
            return;
        }
        String flatAll = flat(tokens, i, j);
        if (currentColumn(out, opt) + flatAll.length() <= opt.maxLineWidth) {
            out.append(flatAll);
            return;
        }
        // break 模式：前导注释先输出（与后随内容同行）
        int headStart = i;
        while (headStart < j && tokens.get(headStart).type == LiteFlowElToken.Type.COMMENT) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(tokens.get(headStart).text);
            headStart++;
        }
        // 区间被前导注释占满（如赋值后仅剩注释）时注释已输出完毕，直接返回，避免 headEnd 越界
        if (headStart >= j) {
            return;
        }
        int headEndIdx = headEnd(tokens, match, headStart, j);
        if (headEndIdx >= j) {
            breakHead(out, tokens, match, headStart, j, level, opt);
            return;
        }
        emitHead(out, tokens, match, headStart, headEndIdx, level, opt);
        int k = headEndIdx;
        while (k < j) {
            // 防御：头部之后剩余的不是 "." 续写段（如尾随注释/多余 token）时平铺粘连，按不可拆处理
            if (k + 1 >= j || !".".equals(tokens.get(k).text)) {
                // 残余以注释开头时与前文之间补一个空格（flat 不带前导空格）
                if (tokens.get(k).type == LiteFlowElToken.Type.COMMENT && out.length() > 0) {
                    char last = out.charAt(out.length() - 1);
                    if (last != ' ' && last != '\n') {
                        out.append(' ');
                    }
                }
                out.append(flat(tokens, k, j));
                return;
            }
            int segEnd = segmentEnd(tokens, match, k, j);
            String suffix = flat(tokens, k, j);
            if (currentColumn(out, opt) + suffix.length() <= opt.maxLineWidth) {
                out.append(suffix);
                return;
            }
            emitSegmentBroken(out, tokens, match, k, segEnd, level, opt);
            k = segEnd;
        }
    }

    /** 头段结束下标：首个 token（可为前导注释之后的词/'('）+ 紧随其后的分组。 */
    private static int headEnd(@NotNull List<LiteFlowElToken> tokens, int[] match, int i, int j) {
        if ("(".equals(tokens.get(i).text)) {
            return match[i] + 1;
        }
        int k = i + 1;
        if (k < j && "(".equals(tokens.get(k).text)) {
            return match[k] + 1;
        }
        return k;
    }

    /** 输出头段：能平铺则平铺，否则断开其分组。 */
    private static void emitHead(@NotNull StringBuilder out, @NotNull List<LiteFlowElToken> tokens,
                                 int[] match, int i, int headEndIdx, int level, @NotNull ElFormatOptions opt) {
        String headFlat = flat(tokens, i, headEndIdx);
        if (currentColumn(out, opt) + headFlat.length() <= opt.maxLineWidth) {
            out.append(headFlat);
            return;
        }
        breakHead(out, tokens, match, i, headEndIdx, level, opt);
    }

    /** 续写段结束下标：'.' IDENT [ '(' args ')' ]。 */
    private static int segmentEnd(@NotNull List<LiteFlowElToken> tokens, int[] match, int k, int j) {
        int end = k + 2;
        if (end < j && "(".equals(tokens.get(end).text)) {
            end = match[end] + 1;
        }
        return end;
    }

    /** 断开一个续写段：修饰符段永不拆开；.TO/.to 段参数两两一行；其余段参数逐个一行。 */
    private static void emitSegmentBroken(@NotNull StringBuilder out, @NotNull List<LiteFlowElToken> tokens,
                                          int[] match, int k, int segEnd, int level, @NotNull ElFormatOptions opt) {
        String keyword = tokens.get(k + 1).text;
        int openIdx = (segEnd - k >= 4 && "(".equals(tokens.get(k + 2).text)) ? k + 2 : -1;
        if (openIdx < 0 || LiteFlowXmlUtil.isDotModifier(keyword)) {
            out.append(flat(tokens, k, segEnd));
            return;
        }
        boolean pairMode = "TO".equalsIgnoreCase(keyword);
        out.append('.').append(keyword).append('(');
        emitBrokenGroupInterior(out, tokens, match, openIdx, level, pairMode, opt);
    }

    /** 断开头部分组：前缀 + '('，参数逐行，闭合 ')' 回到 level 缩进；无分组或分组不覆盖到末尾时平铺（防御）。 */
    private static void breakHead(@NotNull StringBuilder out, @NotNull List<LiteFlowElToken> tokens,
                                  int[] match, int i, int j, int level, @NotNull ElFormatOptions opt) {
        int openIdx = -1;
        if ("(".equals(tokens.get(i).text)) {
            openIdx = i;
        } else if (i + 1 < j && "(".equals(tokens.get(i + 1).text)) {
            openIdx = i + 1;
        }
        if (openIdx < 0 || match[openIdx] + 1 != j) {
            out.append(flat(tokens, i, j));
            return;
        }
        if (openIdx == i) {
            out.append('(');
        } else {
            out.append(tokens.get(i).text).append('(');
        }
        emitBrokenGroupInterior(out, tokens, match, openIdx, level, false, opt);
    }

    /**
     * 输出已展开分组的内部（openIdx 为 '(' 的下标）：按顶层逗号切分参数，逐个换行输出，
     * 最后闭合 ')'。pairMode 时参数两两一行（供 .TO/.to 续写段使用）。
     */
    private static void emitBrokenGroupInterior(@NotNull StringBuilder out, @NotNull List<LiteFlowElToken> tokens,
                                                int[] match, int openIdx, int level, boolean pairMode,
                                                @NotNull ElFormatOptions opt) {
        int closeIdx = match[openIdx];
        List<int[]> args = splitTopLevelArgs(tokens, openIdx + 1, closeIdx);
        if (args.isEmpty()) {
            out.append(')');
            return;
        }
        for (int a = 0; a < args.size(); a++) {
            boolean sameLine = pairMode && a % 2 == 1;
            if (sameLine) {
                out.append(' ');
            } else {
                newlineIndent(out, opt.baseIndent + (level + 1) * opt.indentWidth);
            }
            emitExpr(out, tokens, match, args.get(a)[0], args.get(a)[1], level + 1, opt);
            if (a < args.size() - 1) {
                out.append(',');
            }
        }
        newlineIndent(out, opt.baseIndent + level * opt.indentWidth);
        out.append(')');
    }

    /** 将 [from, to) 区间按括号深度 0 的逗号切分为若干参数区间（注释随相邻参数）。 */
    private static @NotNull List<int[]> splitTopLevelArgs(@NotNull List<LiteFlowElToken> tokens, int from, int to) {
        List<int[]> args = new java.util.ArrayList<>();
        int depth = 0;
        int segStart = from;
        for (int k = from; k < to; k++) {
            LiteFlowElToken t = tokens.get(k);
            if (t.type != LiteFlowElToken.Type.PUNCT) {
                continue;
            }
            if ("(".equals(t.text)) {
                depth++;
            } else if (")".equals(t.text)) {
                depth--;
            } else if (",".equals(t.text) && depth == 0) {
                if (segStart < k) {
                    args.add(new int[]{segStart, k});
                }
                segStart = k + 1;
            }
        }
        if (segStart < to) {
            args.add(new int[]{segStart, to});
        }
        return args;
    }

    /** 当前输出位置的列号（首行计入 firstLineColumn）。 */
    static int currentColumn(@NotNull StringBuilder out, @NotNull ElFormatOptions opt) {
        int lastNl = out.lastIndexOf("\n");
        if (lastNl < 0) {
            return opt.firstLineColumn + out.length();
        }
        return out.length() - lastNl - 1;
    }

    /** 换行并输出 columns 个空格缩进。 */
    static void newlineIndent(@NotNull StringBuilder out, int columns) {
        out.append('\n');
        for (int k = 0; k < columns; k++) {
            out.append(' ');
        }
    }

    /** 从 from 起是否还存在非注释 token。 */
    static boolean hasNonComment(@NotNull List<LiteFlowElToken> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) {
            if (tokens.get(i).type != LiteFlowElToken.Type.COMMENT) {
                return true;
            }
        }
        return false;
    }

    /**
     * 括号配对：返回数组 match，match[i] 为与 tokens[i] 配对的括号 token 下标（'(' ↔ ')' 互相指向），
     * 非括号 token 为 -1；括号不匹配时返回 null。
     */
    static int @Nullable [] matchParens(@NotNull List<LiteFlowElToken> tokens) {
        int[] match = new int[tokens.size()];
        java.util.Arrays.fill(match, -1);
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < tokens.size(); i++) {
            LiteFlowElToken t = tokens.get(i);
            if (t.type != LiteFlowElToken.Type.PUNCT) {
                continue;
            }
            if ("(".equals(t.text)) {
                stack.push(i);
            } else if (")".equals(t.text)) {
                if (stack.isEmpty()) {
                    return null;
                }
                int open = stack.pop();
                match[open] = i;
                match[i] = open;
            }
        }
        return stack.isEmpty() ? match : null;
    }

    /** 将 [i, j) 区间的 token 按空格规则拼成一行（不做任何换行）。 */
    static @NotNull String flat(@NotNull List<LiteFlowElToken> tokens, int i, int j) {
        StringBuilder sb = new StringBuilder();
        LiteFlowElToken prev = null;
        for (int k = i; k < j; k++) {
            LiteFlowElToken t = tokens.get(k);
            if (prev != null && spaceBetween(prev, t)) {
                sb.append(' ');
            }
            sb.append(t.text);
            prev = t;
        }
        return sb.toString();
    }

    /** 平铺输出时两个相邻 token 之间是否需要空格。 */
    static boolean spaceBetween(@NotNull LiteFlowElToken a, @NotNull LiteFlowElToken b) {
        String at = a.text;
        String bt = b.text;
        if (a.type == LiteFlowElToken.Type.PUNCT) {
            if ("(".equals(at) || ".".equals(at)) return false;
            if (",".equals(at) || "=".equals(at) || ";".equals(at)) return true;
            if (")".equals(at)) return false;
        }
        if (b.type == LiteFlowElToken.Type.PUNCT) {
            if (")".equals(bt) || ",".equals(bt) || ";".equals(bt) || "(".equals(bt) || ".".equals(bt)) return false;
            if ("=".equals(bt)) return true;
        }
        // 其余（词/字符串/注释相邻）保底一个空格
        return true;
    }
}
