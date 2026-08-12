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
        // 本任务仅做单行平铺；语句拆分与 fits-or-break 在后续任务加入
        return FormatResult.success(flat(tokens, 0, tokens.size()));
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
