package com.yomahub.liteflowhelper.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link LiteFlowElFormatter} 的单元测试。
 */
public class LiteFlowElFormatterTest {

    private static FormatResult fmt(String s) {
        return LiteFlowElFormatter.format(s, ElFormatOptions.defaults());
    }

    @Test
    public void normalizesWhitespaceInSingleLine() {
        FormatResult r = fmt("THEN( a ,WHEN( b,c ) ,IF(x,d).ELSE( e ));");
        assertTrue(r.success);
        assertEquals("THEN(a, WHEN(b, c), IF(x, d).ELSE(e));", r.formatted);
    }

    @Test
    public void normalizesSubVariableAssignment() {
        FormatResult r = fmt("sub=THEN(a);");
        assertTrue(r.success);
        assertEquals("sub = THEN(a);", r.formatted);
    }

    @Test
    public void stringLiteralContentsAreUntouched() {
        FormatResult r = fmt("THEN(a,\"x,(;y\");");
        assertTrue(r.success);
        assertEquals("THEN(a, \"x,(;y\");", r.formatted);
    }

    @Test
    public void unbalancedParensReturnFailure() {
        FormatResult r = fmt("THEN(a, b");
        assertFalse(r.success);
        assertTrue(r.reason.contains("括号不匹配"));
    }

    @Test
    public void emptyElReturnsFailure() {
        assertFalse(fmt("   ").success);
        assertFalse(fmt("/* 只有注释 */").success);
    }

    @Test
    public void statementsAreSplitOntoOwnLines() {
        FormatResult r = fmt("sub = THEN(a,b);THEN(sub,c);");
        assertTrue(r.success);
        assertEquals("sub = THEN(a, b);\nTHEN(sub, c);", r.formatted);
    }

    @Test
    public void statementContinuationUsesBaseIndent() {
        FormatResult r = LiteFlowElFormatter.format("sub = THEN(a,b);THEN(sub,c);",
                new ElFormatOptions(80, 4, 8, 8));
        assertTrue(r.success);
        assertEquals("sub = THEN(a, b);\n        THEN(sub, c);", r.formatted);
    }

    @Test
    public void missingTrailingSemicolonIsPreserved() {
        FormatResult r = fmt("sub = THEN(a); THEN(sub)");
        assertTrue(r.success);
        assertEquals("sub = THEN(a);\nTHEN(sub)", r.formatted);
    }

    @Test
    public void trailingCommentGluesToLastStatementLine() {
        FormatResult r = fmt("THEN(a); /* 说明 */");
        assertTrue(r.success);
        assertEquals("THEN(a); /* 说明 */", r.formatted);
    }

    @Test
    public void commentBeforeStatementStartsItsLine() {
        FormatResult r = fmt("THEN(a); /* c */ WHEN(b);");
        assertTrue(r.success);
        assertEquals("THEN(a);\n/* c */ WHEN(b);", r.formatted);
    }
}
