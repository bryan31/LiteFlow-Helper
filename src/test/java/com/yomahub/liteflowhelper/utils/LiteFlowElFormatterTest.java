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

    @Test
    public void switchToChainExpandsWithPairedToArgs() {
        FormatResult r = fmt("SWITCH(channel).TO(\"app\", appProcess, \"web\", webProcess, \"h5\", h5Process, \"api\", apiProcess).DEFAULT(defaultProcess);");
        assertTrue(r.success);
        assertEquals("SWITCH(channel).TO(\n"
                + "    \"app\", appProcess,\n"
                + "    \"web\", webProcess,\n"
                + "    \"h5\", h5Process,\n"
                + "    \"api\", apiProcess\n"
                + ").DEFAULT(defaultProcess);", r.formatted);
    }

    @Test
    public void elifElseChainBreaksAtFirstNonFittingSegment() {
        FormatResult r = fmt("IF(vipUserCheck, vipDiscountProcess).ELIF(newUserCheck, newUserGiftProcess).ELSE(normalPriceProcess);");
        assertTrue(r.success);
        assertEquals("IF(vipUserCheck, vipDiscountProcess).ELIF(\n"
                + "    newUserCheck,\n"
                + "    newUserGiftProcess\n"
                + ").ELSE(normalPriceProcess);", r.formatted);
    }

    @Test
    public void shortChainStaysOnOneLine() {
        FormatResult r = fmt("FOR(countNode).DO(loopProcess);");
        assertTrue(r.success);
        assertEquals("FOR(countNode).DO(loopProcess);", r.formatted);
    }

    @Test
    public void dotModifiersStayGluedInsideBrokenGroup() {
        FormatResult r = fmt("THEN(a.tag(\"app\").data(\"ctx\"),WHEN(stockCheckNode,priceCheckNode,inventoryValidateNode,riskControlNode,auditLogNode),b);");
        assertTrue(r.success);
        assertEquals("THEN(\n"
                + "    a.tag(\"app\").data(\"ctx\"),\n"
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
    public void modifierSegmentItselfNeverBreaks() {
        // 修饰符段自身超宽时平铺溢出，不拆开 .tag(...)
        FormatResult r = fmt("THEN(someNodeComponentWithLongName01.tag(\"someExtremelyLongTagValue012345678901234\"), b);");
        assertTrue(r.success);
        assertEquals("THEN(\n"
                + "    someNodeComponentWithLongName01.tag(\"someExtremelyLongTagValue012345678901234\"),\n"
                + "    b\n"
                + ");", r.formatted);
    }

    @Test
    public void chainFormattingIsIdempotent() {
        String el = "SWITCH(channel).TO(\"app\", appProcess, \"web\", webProcess, \"h5\", h5Process, \"api\", apiProcess).DEFAULT(defaultProcess);";
        FormatResult once = fmt(el);
        assertTrue(once.success);
        FormatResult twice = fmt(once.formatted);
        assertTrue(twice.success);
        assertEquals(once.formatted, twice.formatted);
    }

    @Test
    public void trailingCommentInsideOverwideGroupDoesNotDuplicateCloseParen() {
        // 回归：组内最后一个参数带尾随注释且参数行超宽时，续写段循环曾把 tokens 里闭合分组的 ')'
        // 当作段内容重复输出一次（合法 EL 变非法、再次格式化括号不平衡）
        String n3 = "thirdLongNodeNameForCoverage03WithExtraPadding0123456789ABCDEFGHIJKLMNOP"; // 72 字符，使参数行平铺超宽
        String el = "THEN(aVeryLongNodeNameForCoverage01, anotherLongNodeNameForCoverage02, " + n3 + " /* 末尾 */);";
        FormatResult r = fmt(el);
        assertTrue(r.success);
        assertEquals("THEN(\n"
                + "    aVeryLongNodeNameForCoverage01,\n"
                + "    anotherLongNodeNameForCoverage02,\n"
                + "    " + n3 + " /* 末尾 */\n"
                + ");", r.formatted);
        // 不出现连续两个 ')' 的重复闭合
        assertFalse(r.formatted.contains("))"));
        // 幂等：修复前第二次 format 会因括号不平衡直接失败
        FormatResult twice = fmt(r.formatted);
        assertTrue(twice.success);
        assertEquals(r.formatted, twice.formatted);
    }

    @Test
    public void loneCommentAfterEdgeGluedHeadDoesNotCrash() {
        // 回归：头部平铺 79 列贴边（不超宽故不展开），其后孤立残余只有 1 个注释 token 且无分号时，
        // 续写段循环曾越界访问 tokens.get(k+1) 抛 IndexOutOfBoundsException
        String el = "THEN(nodeAlpha, nodeBeta, nodeGamma, nodeDelta, nodeEpsilon, nodeZeta, nodeEta) /* 注 */";
        FormatResult r = fmt(el);
        assertTrue(r.success);
        // 残余注释平铺粘连到头部之后，与前文之间补一个空格
        assertEquals("THEN(nodeAlpha, nodeBeta, nodeGamma, nodeDelta, nodeEpsilon, nodeZeta, nodeEta) /* 注 */",
                r.formatted);
        FormatResult twice = fmt(r.formatted);
        assertTrue(twice.success);
        assertEquals(r.formatted, twice.formatted);
    }

    @Test
    public void cdataWrappedElIsRejected() {
        // 回归：CDATA 写法的 chain 值文本含 <![CDATA[ / ]]> 标记，重排会损坏 XML，必须拒收
        FormatResult r = fmt("<![CDATA[THEN(a);]]>");
        assertFalse(r.success);
        assertTrue(r.reason.contains("CDATA"));
        // 前导空白之后紧跟 CDATA 标记同样拒收
        FormatResult padded = fmt("  \n<![CDATA[THEN(a);]]>");
        assertFalse(padded.success);
        assertTrue(padded.reason.contains("CDATA"));
    }
}
