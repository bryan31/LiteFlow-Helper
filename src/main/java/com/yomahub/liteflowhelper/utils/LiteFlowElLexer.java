package com.yomahub.liteflowhelper.utils;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * LiteFlow EL 表达式的轻量手写分词器。
 * <p>
 * 取代原先依赖 QLExpress 3.x {@code getOutVarNames} 的解析方式：
 * 原方式存在版本不匹配（LiteFlow 实际跑在 qlexpress4）、字符串字面量内容被误判为变量、
 * 合法 EL 解析失败时整条 chain 静默失效等问题。
 * </p>
 * <p>
 * 该分词器为纯 Java 实现，不依赖 IntelliJ 平台，单次线性扫描，永不抛异常。
 * </p>
 */
public final class LiteFlowElLexer {

    private LiteFlowElLexer() {
    }

    /**
     * 将一段 LiteFlow EL 文本分词。
     *
     * @param text EL 文本（通常是 {@code <chain>} 标签的值文本）
     * @return 按出现顺序排列的 token 列表，偏移均相对该 text；永不为 null
     */
    public static @NotNull List<LiteFlowElToken> tokenize(@NotNull String text) {
        List<LiteFlowElToken> tokens = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);

            // 空白
            if (c <= ' ') {
                i++;
                continue;
            }

            // 块注释 /* ... */ （也兼容 /** ... **/ 写法）
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < n && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) {
                    i++;
                }
                if (i + 1 < n) {
                    i += 2; // 消费闭合的 */
                } else {
                    i = n; // 未闭合，消费到末尾，不抛异常
                }
                tokens.add(new LiteFlowElToken(LiteFlowElToken.Type.COMMENT, text.substring(start, i), start, i));
                continue;
            }

            // 字符串字面量 "..." 或 '...'
            if (c == '"' || c == '\'') {
                char quote = c;
                int start = i;
                i++;
                while (i < n && text.charAt(i) != quote) {
                    if (text.charAt(i) == '\\' && i + 1 < n) {
                        i += 2; // 转义字符
                    } else {
                        i++;
                    }
                }
                if (i < n) {
                    i++; // 闭合引号
                }
                tokens.add(new LiteFlowElToken(LiteFlowElToken.Type.STRING, text.substring(start, i), start, i));
                continue;
            }

            // 数字字面量（含小数）
            if (isDigit(c)) {
                int start = i;
                while (i < n && isDigit(text.charAt(i))) {
                    i++;
                }
                // 仅当下一个是 ".数字" 时才把小数部分并入，避免吞掉方法调用后的点（如 5.DO）
                if (i + 1 < n && text.charAt(i) == '.' && isDigit(text.charAt(i + 1))) {
                    i++; // .
                    while (i < n && isDigit(text.charAt(i))) {
                        i++;
                    }
                }
                tokens.add(new LiteFlowElToken(LiteFlowElToken.Type.NUMBER, text.substring(start, i), start, i));
                continue;
            }

            // 标识符 [a-zA-Z_][a-zA-Z0-9_]*
            if (isIdentStart(c)) {
                int start = i;
                while (i < n && isIdentPart(text.charAt(i))) {
                    i++;
                }
                tokens.add(new LiteFlowElToken(LiteFlowElToken.Type.IDENT, text.substring(start, i), start, i));
                continue;
            }

            // 其余单字符作为标点/操作符
            tokens.add(new LiteFlowElToken(LiteFlowElToken.Type.PUNCT, String.valueOf(c), i, i + 1));
            i++;
        }
        return tokens;
    }

    /**
     * 从 token 流中找出子变量定义（形如 {@code sub = THEN(a)} 中的 {@code sub}）。
     * <p>
     * 判定规则：一个 IDENT 紧跟一个 PUNCT "="（中间可有空白，因空白不出现在 token 流中）。
     * 返回 map 的 key 为子变量名，value 为其定义处的 IDENT token（含偏移），便于高亮/跳转。
     * 同名多次定义时保留第一个。
     * </p>
     */
    public static @NotNull java.util.Map<String, LiteFlowElToken> findSubVarDefinitions(@NotNull List<LiteFlowElToken> tokens) {
        java.util.Map<String, LiteFlowElToken> defs = new java.util.LinkedHashMap<>();
        for (int k = 0; k < tokens.size(); k++) {
            LiteFlowElToken t = tokens.get(k);
            if (t.type != LiteFlowElToken.Type.IDENT) {
                continue;
            }
            if (k + 1 < tokens.size()) {
                LiteFlowElToken next = tokens.get(k + 1);
                if (next.type == LiteFlowElToken.Type.PUNCT && "=".equals(next.text)) {
                    defs.putIfAbsent(t.text, t);
                }
            }
        }
        return defs;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }
}
