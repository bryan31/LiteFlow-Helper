package com.yomahub.liteflowhelper.toolwindow.service;

import com.intellij.openapi.project.Project;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 负责返回 LiteFlow Chain 定义。
 */
public class LiteFlowChainScanner {

    public List<ChainInfo> findChains(@NotNull Project project) {
        return findChains(project, new LiteFlowXmlScanner().scan(project));
    }

    public List<ChainInfo> findChains(@NotNull Project project, @NotNull LiteFlowXmlScanResult xmlScanResult) {
        return xmlScanResult.getChains();
    }
}
