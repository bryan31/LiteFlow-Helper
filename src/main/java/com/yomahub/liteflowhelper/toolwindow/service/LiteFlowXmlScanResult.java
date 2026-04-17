package com.yomahub.liteflowhelper.toolwindow.service;

import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * 单次 XML 扫描的聚合结果。
 */
public final class LiteFlowXmlScanResult {
    private static final LiteFlowXmlScanResult EMPTY =
            new LiteFlowXmlScanResult(Collections.emptyList(), Collections.emptyList());

    private final List<ChainInfo> chains;
    private final List<LiteFlowNodeInfo> xmlNodes;

    public LiteFlowXmlScanResult(@NotNull List<ChainInfo> chains, @NotNull List<LiteFlowNodeInfo> xmlNodes) {
        this.chains = Collections.unmodifiableList(chains);
        this.xmlNodes = Collections.unmodifiableList(xmlNodes);
    }

    public static LiteFlowXmlScanResult empty() {
        return EMPTY;
    }

    @NotNull
    public List<ChainInfo> getChains() {
        return chains;
    }

    @NotNull
    public List<LiteFlowNodeInfo> getXmlNodes() {
        return xmlNodes;
    }
}
