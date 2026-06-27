package com.yomahub.liteflowhelper.utils;

import java.util.Objects;

/**
 * LiteFlow EL 表达式的一个词法 token。
 * <p>
 * 由 {@link LiteFlowElLexer} 产出，携带 token 类型、文本以及在源串中的偏移区间 [start, end)。
 * 这是一个不可变的纯数据结构，不依赖任何 IntelliJ 平台 API，便于单元测试。
 * </p>
 */
public final class LiteFlowElToken {

    /**
     * token 类型。
     * <ul>
     *   <li>{@link #COMMENT} —— 块注释 {@code /* ... *}{@code /}</li>
     *   <li>{@link #STRING} —— 字符串字面量 {@code "..."} 或 {@code '...'}（内容不会被视为标识符）</li>
     *   <li>{@link #NUMBER} —— 数字字面量</li>
     *   <li>{@link #IDENT} —— 标识符（可能是 EL 关键字、节点/链 id、子变量）</li>
     *   <li>{@link #PUNCT} —— 标点 / 操作符（如 {@code ( ) , . ; = :}）</li>
     * </ul>
     */
    public enum Type { COMMENT, STRING, NUMBER, IDENT, PUNCT }

    public final Type type;
    public final String text;
    public final int start;
    public final int end;

    public LiteFlowElToken(Type type, String text, int start, int end) {
        this.type = type;
        this.text = text;
        this.start = start;
        this.end = end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LiteFlowElToken)) return false;
        LiteFlowElToken that = (LiteFlowElToken) o;
        return start == that.start && end == that.end
                && type == that.type && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, text, start, end);
    }

    @Override
    public String toString() {
        return type + "{" + text + "}[" + start + "," + end + ")";
    }
}
