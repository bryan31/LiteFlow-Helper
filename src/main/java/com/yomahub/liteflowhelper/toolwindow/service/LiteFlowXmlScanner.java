package com.yomahub.liteflowhelper.toolwindow.service;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.model.NodeType;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 单次遍历 XML 文件，同时提取 chains 和 XML 节点。
 */
public final class LiteFlowXmlScanner {
    private static final Logger LOG = Logger.getInstance(LiteFlowXmlScanner.class);

    @NotNull
    public LiteFlowXmlScanResult scan(@NotNull Project project) {
        return ScannerUtil.runInReadAction(project, "LiteFlow XML", LiteFlowXmlScanResult.empty(), () -> {
            List<ChainInfo> chainInfos = new ArrayList<>();
            List<LiteFlowNodeInfo> xmlNodeInfos = new ArrayList<>();
            Collection<VirtualFile> virtualFiles =
                    FileTypeIndex.getFiles(XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project));
            PsiManager psiManager = PsiManager.getInstance(project);

            for (VirtualFile virtualFile : virtualFiles) {
                if (project.isDisposed()) {
                    return LiteFlowXmlScanResult.empty();
                }

                PsiFile psiFile = psiManager.findFile(virtualFile);
                if (!(psiFile instanceof XmlFile xmlFile)) {
                    continue;
                }

                XmlTag flowRootTag = xmlFile.getDocument() != null ? xmlFile.getDocument().getRootTag() : null;
                if (flowRootTag == null || !"flow".equals(flowRootTag.getName())) {
                    continue;
                }

                XmlTag[] chainTags = flowRootTag.findSubTags("chain");
                if (chainTags.length == 0) {
                    continue;
                }

                for (XmlTag chainTag : chainTags) {
                    String chainId = chainTag.getAttributeValue("id");
                    String chainName = chainTag.getAttributeValue("name");
                    String finalName = !StringUtil.isEmpty(chainName) ? chainName : chainId;
                    if (!StringUtil.isEmpty(finalName)) {
                        chainInfos.add(new ChainInfo(finalName, xmlFile, chainTag.getTextOffset()));
                    }
                }

                XmlTag nodesTag = LiteFlowXmlUtil.getNodesTag(flowRootTag);
                if (nodesTag == null) {
                    continue;
                }

                for (XmlTag nodeTag : nodesTag.findSubTags("node")) {
                    String xmlId = nodeTag.getAttributeValue("id");
                    if (StringUtil.isEmpty(xmlId)) {
                        LOG.warn("跳过 XML 节点，缺少 id 属性: " + xmlFile.getName());
                        continue;
                    }

                    String xmlName = nodeTag.getAttributeValue("name");
                    String typeAttr = nodeTag.getAttributeValue("type");
                    NodeType nodeType = NodeType.fromXmlType(typeAttr);
                    xmlNodeInfos.add(new LiteFlowNodeInfo(xmlId, xmlName, nodeType, nodeTag, "XML脚本"));
                }
            }

            chainInfos.sort(Comparator.comparing(ChainInfo::getName));
            xmlNodeInfos.sort(Comparator.comparing(LiteFlowNodeInfo::getNodeId));
            return new LiteFlowXmlScanResult(chainInfos, xmlNodeInfos);
        });
    }
}
