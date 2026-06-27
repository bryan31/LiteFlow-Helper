package com.yomahub.liteflowhelper.utils;

import com.yomahub.liteflowhelper.toolwindow.model.NodeCategory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * LiteFlow EL 表达式校验器（纯逻辑，无 PSI 依赖，便于单元测试）。
 * <p>
 * 输入 EL 文本与一个 name→{@link NodeCategory} 解析函数，产出结构与语义问题：
 * <ul>
 *   <li>结构：括号不匹配(ERROR)、SWITCH 缺 .TO / FOR·WHILE·ITERATOR 缺 .DO(WARNING)、IF 参数 &lt;2(ERROR)</li>
 *   <li>语义：节点类型与所在关键字槽位不匹配(ERROR)，对齐框架 OperatorHelper 的解析期硬性检查</li>
 * </ul>
 * 解析基于 {@link LiteFlowElLexer} 产出的 token，用括号栈跟踪每个节点引用的 (所在关键字, 参数位)。
 * {@link NodeCategory#FALLBACK} 与 {@link NodeCategory#UNKNOWN}（及未知节点）一律放行。
 * </p>
 */
public final class LiteFlowElAnalyzer {

    private LiteFlowElAnalyzer() {
    }

    /** 一个括号层：所在关键字、当前参数位（0-based，遇顶层逗号 +1）、对应 '(' 的 token 索引。 */
    private static final class Frame {
        final String keyword; // IF/SWITCH/THEN/AND/... ，null 表示未知/分组
        int argIndex = 0;
        final int openTokenIndex;

        Frame(String keyword, int openTokenIndex) {
            this.keyword = keyword;
            this.openTokenIndex = openTokenIndex;
        }
    }

    public static List<ElValidationFinding> analyze(String text, Function<String, NodeCategory> categoryOf) {
        List<ElValidationFinding> findings = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return findings;
        }
        List<LiteFlowElToken> tokens = LiteFlowElLexer.tokenize(text);
        Deque<Frame> stack = new ArrayDeque<>();
        int parenBalance = 0;

        for (int i = 0; i < tokens.size(); i++) {
            LiteFlowElToken t = tokens.get(i);

            if (t.type == LiteFlowElToken.Type.PUNCT) {
                if ("(".equals(t.text)) {
                    parenBalance++;
                    String kw = null;
                    if (i > 0 && tokens.get(i - 1).type == LiteFlowElToken.Type.IDENT
                            && LiteFlowXmlUtil.isElKeyword(tokens.get(i - 1).text)) {
                        kw = tokens.get(i - 1).text;
                    }
                    stack.push(new Frame(kw, i));
                } else if (")".equals(t.text)) {
                    parenBalance--;
                    if (!stack.isEmpty()) {
                        checkOnClose(stack.pop(), i, tokens, findings);
                    }
                } else if (",".equals(t.text)) {
                    if (!stack.isEmpty()) {
                        stack.peek().argIndex++;
                    }
                }
            } else if (t.type == LiteFlowElToken.Type.IDENT && !LiteFlowXmlUtil.isElKeyword(t.text)) {
                // 节点引用：按所在槽位做类型校验
                NodeCategory actual = categoryOf.apply(t.text);
                if (actual == null || actual == NodeCategory.FALLBACK || actual == NodeCategory.UNKNOWN) {
                    continue;
                }
                if (!stack.isEmpty()) {
                    Frame f = stack.peek();
                    NodeCategory required = requiredCategory(f.keyword, f.argIndex);
                    if (required != null && required != actual) {
                        findings.add(new ElValidationFinding(ElValidationFinding.Severity.ERROR, t.start, t.end,
                                "节点 '" + t.text + "' 应为 " + required + " 类型（用于 " + f.keyword + "）"));
                    }
                }
            }
        }

        if (parenBalance != 0) {
            findings.add(new ElValidationFinding(ElValidationFinding.Severity.ERROR,
                    0, Math.max(text.length(), 1), "EL 表达式括号不匹配"));
        }
        return findings;
    }

    private static void checkOnClose(Frame f, int closeIdx, List<LiteFlowElToken> tokens,
                                     List<ElValidationFinding> findings) {
        String kw = f.keyword;
        if (kw == null) {
            return;
        }
        int kwStart = tokens.get(f.openTokenIndex - 1).start;
        int kwEnd = tokens.get(f.openTokenIndex - 1).end;

        // IF 至少 2 个参数（条件 + then 分支）
        if ("IF".equals(kw)) {
            int argCount = f.argIndex + 1;
            if (argCount < 2) {
                findings.add(new ElValidationFinding(ElValidationFinding.Severity.ERROR, kwStart, kwEnd,
                        "IF 至少需要 2 个参数（条件 + then 分支）"));
            }
        }

        // SWITCH 需 .TO；FOR/WHILE/ITERATOR 需 .DO（紧跟在 ')' 之后）
        String need = null;
        if ("SWITCH".equals(kw)) {
            need = "TO";
        } else if ("FOR".equals(kw) || "WHILE".equals(kw) || "ITERATOR".equals(kw)) {
            need = "DO";
        }
        if (need != null && !hasContinuation(tokens, closeIdx, need)) {
            String msg = "SWITCH".equals(kw) ? "SWITCH 应跟随 .TO(...)" : kw + " 应跟随 .DO(...)";
            findings.add(new ElValidationFinding(ElValidationFinding.Severity.WARNING, kwStart, kwEnd, msg));
        }
    }

    /** 判断 closeIdx（对应 ')'）之后是否紧跟 "." + 指定关键字（大小写不敏感）。 */
    private static boolean hasContinuation(List<LiteFlowElToken> tokens, int closeIdx, String need) {
        if (closeIdx + 2 >= tokens.size()) {
            return false;
        }
        LiteFlowElToken dot = tokens.get(closeIdx + 1);
        LiteFlowElToken next = tokens.get(closeIdx + 2);
        return dot.type == LiteFlowElToken.Type.PUNCT && ".".equals(dot.text)
                && next.type == LiteFlowElToken.Type.IDENT && need.equalsIgnoreCase(next.text);
    }

    /** 某关键字某参数位所需的节点类型；返回 null 表示该槽位不做类型约束。 */
    private static NodeCategory requiredCategory(String keyword, int argIndex) {
        if (keyword == null) {
            return null;
        }
        switch (keyword) {
            case "SWITCH":
                return argIndex == 0 ? NodeCategory.SWITCH : null;
            case "IF":
            case "WHILE":
                return argIndex == 0 ? NodeCategory.BOOLEAN : null;
            case "FOR":
                return argIndex == 0 ? NodeCategory.FOR : null;
            case "ITERATOR":
                return argIndex == 0 ? NodeCategory.ITERATOR : null;
            case "AND":
            case "OR":
                return NodeCategory.BOOLEAN; // 所有操作数都需 boolean
            case "NOT":
            case "BREAK":
                return argIndex == 0 ? NodeCategory.BOOLEAN : null;
            default:
                return null;
        }
    }
}
