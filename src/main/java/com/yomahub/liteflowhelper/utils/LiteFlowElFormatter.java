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
     * 输出 [i, j) 区间的一个表达式：整体平铺能放入剩余行宽则平铺，
     * 否则断开头部分组（参数逐个换行缩进）。点号续写链的处理在后续任务加入。
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
        breakHead(out, tokens, match, headStart, j, level, opt);
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
     * 最后闭合 ')'。pairMode 时参数两两一行（供 .TO 续写使用，见 Task 4）。
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
