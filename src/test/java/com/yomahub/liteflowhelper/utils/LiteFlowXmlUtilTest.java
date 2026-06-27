package com.yomahub.liteflowhelper.utils;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link LiteFlowXmlUtil} 中纯逻辑的单元测试。
 */
public class LiteFlowXmlUtilTest {

    @Test
    public void chainWithoutRouteOrBodyChildrenIsElCarrier() {
        // 直接值写法：<chain>THEN(a);</chain>，chain 自身文本即 EL
        assertTrue(LiteFlowXmlUtil.isElCarrierByStructure("chain", null, false, false));
        assertTrue(LiteFlowXmlUtil.isElCarrierByStructure("chain", "flow", false, false));
    }

    @Test
    public void chainWithRouteOrBodyChildIsNotElCarrierItself() {
        // 有 route/body 子标签时，EL 在子标签里，chain 自身不再是 EL 承载者
        assertFalse(LiteFlowXmlUtil.isElCarrierByStructure("chain", "flow", true, true));
        assertFalse(LiteFlowXmlUtil.isElCarrierByStructure("chain", "flow", false, true));
        assertFalse(LiteFlowXmlUtil.isElCarrierByStructure("chain", "flow", true, false));
    }

    @Test
    public void routeAndBodyUnderChainAreElCarriers() {
        assertTrue(LiteFlowXmlUtil.isElCarrierByStructure("route", "chain", false, false));
        assertTrue(LiteFlowXmlUtil.isElCarrierByStructure("body", "chain", false, false));
    }

    @Test
    public void routeOrBodyNotDirectlyUnderChainIsNotElCarrier() {
        assertFalse(LiteFlowXmlUtil.isElCarrierByStructure("route", "flow", false, false));
        assertFalse(LiteFlowXmlUtil.isElCarrierByStructure("body", "something", false, false));
        assertFalse(LiteFlowXmlUtil.isElCarrierByStructure("body", null, false, false));
    }

    @Test
    public void otherTagsAreNotElCarriers() {
        assertFalse(LiteFlowXmlUtil.isElCarrierByStructure("node", "nodes", false, false));
        assertFalse(LiteFlowXmlUtil.isElCarrierByStructure("flow", null, false, false));
        assertFalse(LiteFlowXmlUtil.isElCarrierByStructure("nodes", "flow", false, false));
        assertFalse(LiteFlowXmlUtil.isElCarrierByStructure(null, null, false, false));
    }

    @Test
    public void afterDotCompletionOffersModifiersAndContinuations() {
        Set<String> afterDot = LiteFlowXmlUtil.getCompletionKeywords(true);
        // 修饰符
        assertTrue(afterDot.contains("tag"));
        assertTrue(afterDot.contains("maxWaitSeconds"));
        assertTrue(afterDot.contains("data"));
        // 结构续写
        assertTrue(afterDot.contains("DO"));
        assertTrue(afterDot.contains("TO"));
        // 不应出现顶层结构关键字
        assertFalse(afterDot.contains("THEN"));
        assertFalse(afterDot.contains("SWITCH"));
        assertFalse(afterDot.contains("IF"));
    }

    @Test
    public void topLevelCompletionOffersStructuralKeywords() {
        Set<String> top = LiteFlowXmlUtil.getCompletionKeywords(false);
        assertTrue(top.contains("THEN"));
        assertTrue(top.contains("SWITCH"));
        assertTrue(top.contains("IF"));
        assertTrue(top.contains("ITERATOR"));
        // 不应出现点号修饰符
        assertFalse(top.contains("tag"));
        assertFalse(top.contains("DO"));
    }
}
