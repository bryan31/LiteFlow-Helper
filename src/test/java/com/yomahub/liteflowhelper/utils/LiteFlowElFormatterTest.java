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

    @Test
    public void overwideGroupExpandsByLevel() {
        FormatResult r = fmt("THEN(orderCheck,WHEN(stockCheck,priceCheck),IF(vipUser,vipDiscount).ELSE(normalPrice),finishNode);");
        assertTrue(r.success);
        assertEquals("THEN(\n"
                + "    orderCheck,\n"
                + "    WHEN(stockCheck, priceCheck),\n"
                + "    IF(vipUser, vipDiscount).ELSE(normalPrice),\n"
                + "    finishNode\n"
                + ");", r.formatted);
    }

    @Test
    public void nestedGroupExpandsRecursively() {
        FormatResult r = fmt("THEN(a,WHEN(stockCheckNode,priceCheckNode,inventoryValidateNode,riskControlNode,auditLogNode),b);");
        assertTrue(r.success);
        assertEquals("THEN(\n"
                + "    a,\n"
                + "    WHEN(\n"
                + "        stockCheckNode,\n"
                + "        priceCheckNode,\n"
                + "        inventoryValidateNode,\n"
                + "        riskControlNode,\n"
                + "        auditLogNode\n"
                + "    ),\n"
                + "    b\n"
                + ");", r.formatted);
    }

    @Test
    public void emptyGroupStaysInline() {
        FormatResult r = fmt("THEN(a, b());");
        assertTrue(r.success);
        assertEquals("THEN(a, b());", r.formatted);
    }

    @Test
    public void firstLineColumnCountsIntoWidthBudget() {
        String el = "THEN(nodeAlpha, nodeBeta, nodeGamma, nodeDelta, nodeEpsilon);";
        // 首行起始列 0：60 列平铺，保持单行
        FormatResult fits = LiteFlowElFormatter.format(el, new ElFormatOptions(80, 4, 0, 0));
        assertTrue(fits.success);
        assertEquals(el, fits.formatted);
        // 首行起始列 30：30+60=90 超宽，展开；续行缩进 baseIndent4 + 层级
        FormatResult breaks = LiteFlowElFormatter.format(el, new ElFormatOptions(80, 4, 4, 30));
        assertTrue(breaks.success);
        assertEquals("THEN(\n"
                + "        nodeAlpha,\n"
                + "        nodeBeta,\n"
                + "        nodeGamma,\n"
                + "        nodeDelta,\n"
                + "        nodeEpsilon\n"
                + "    );", breaks.formatted);
    }

    @Test
    public void leadingCommentInBrokenGroupStaysWithItsArg() {
        FormatResult r = fmt("THEN(/* 前置检查 */ orderCheck,WHEN(stockCheckNode,priceCheckNode,inventoryValidateNode,riskControlNode,auditLogNode),finishNode);");
        assertTrue(r.success);
        assertEquals("THEN(\n"
                + "    /* 前置检查 */ orderCheck,\n"
                + "    WHEN(\n"
                + "        stockCheckNode,\n"
                + "        priceCheckNode,\n"
                + "        inventoryValidateNode,\n"
                + "        riskControlNode,\n"
                + "        auditLogNode\n"
                + "    ),\n"
                + "    finishNode\n"
                + ");", r.formatted);
    }

    @Test
    public void formattingIsIdempotent() {
        String el = "THEN(a,WHEN(stockCheckNode,priceCheckNode,inventoryValidateNode,riskControlNode,auditLogNode),b);";
        FormatResult once = fmt(el);
        assertTrue(once.success);
        FormatResult twice = fmt(once.formatted);
        assertTrue(twice.success);
        assertEquals(once.formatted, twice.formatted);
    }

    @Test
    public void commentOnlyAfterAssignmentDoesNotCrash() {
        // 赋值前缀后仅剩一条超宽注释：前导注释循环会耗尽区间，不得越界崩溃
        String comment = "/* " + "a".repeat(80) + " */";
        FormatResult r = fmt("x = " + comment);
        assertTrue(r.success);
        assertTrue(r.formatted.contains(comment));
    }
}
