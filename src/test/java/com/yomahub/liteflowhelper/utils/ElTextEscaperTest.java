package com.yomahub.liteflowhelper.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@link ElTextEscaper} 的单元测试。
 */
public class ElTextEscaperTest {

    @Test
    public void plainTextIsUnchanged() {
        assertEquals("THEN(a, WHEN(b, c));", ElTextEscaper.escape("THEN(a, WHEN(b, c));"));
    }

    @Test
    public void angleBracketsInsideStringAreEscaped() {
        assertEquals("THEN(a, \"x&gt;y\");", ElTextEscaper.escape("THEN(a, \"x>y\");"));
        assertEquals("THEN(a, \"x&lt;y\");", ElTextEscaper.escape("THEN(a, \"x<y\");"));
    }

    @Test
    public void existingEntityIsNotDoubleEscaped() {
        assertEquals("THEN(a, \"a&lt;b\");", ElTextEscaper.escape("THEN(a, \"a&lt;b\");"));
        assertEquals("THEN(a, \"&#60;\");", ElTextEscaper.escape("THEN(a, \"&#60;\");"));
    }

    @Test
    public void bareAmpersandIsEscaped() {
        assertEquals("THEN(a, \"a&amp;b\");", ElTextEscaper.escape("THEN(a, \"a&b\");"));
    }

    @Test
    public void unknownEntityNameIsEscaped() {
        // &b; 不是 XML 预定义实体，必须转义 & 否则写回后 XML 无法解析
        assertEquals("THEN(a, \"a&amp;b;\");", ElTextEscaper.escape("THEN(a, \"a&b;\");"));
    }

    @Test
    public void escapedQuoteInsideStringIsPreserved() {
        assertEquals("THEN(a, \"s\\\"t\");", ElTextEscaper.escape("THEN(a, \"s\\\"t\");"));
    }

    @Test
    public void angleBracketsOutsideStringAreKept() {
        // 非法 EL 的防御场景：字符串外的 < > 不转义
        assertEquals("THEN(a>b);", ElTextEscaper.escape("THEN(a>b);"));
    }
}
