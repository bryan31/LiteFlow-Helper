package com.yomahub.liteflowhelper.toolwindow.service;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService; // 导入DumbService
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import com.intellij.openapi.diagnostic.Logger; // 可选：用于日志记录

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 负责扫描项目中的 LiteFlow Chain (流程链) 定义。
 * Chain通常在XML配置文件中定义。
 */
public class LiteFlowChainScanner {
    private static final Logger LOG = Logger.getInstance(LiteFlowChainScanner.class); // 可选：用于日志记录

    /**
     * 在项目中查找所有的 LiteFlow Chain 定义。
     * @param project 当前项目
     * @return ChainInfo 列表。如果项目处于Dumb Mode则返回空列表。
     */
    public List<ChainInfo> findChains(Project project) {
        // 检查项目是否处于 "dumb mode" (正在索引)。
        // 如果是，则推迟扫描，避免 IndexNotReadyException。
        if (DumbService.getInstance(project).isDumb()) {
            LOG.info("项目正处于 dumb mode。LiteFlow chain 扫描已推迟。"); // 可选日志
            return Collections.emptyList();
        }

        // PSI访问需要在读操作中进行。
        return ApplicationManager.getApplication().runReadAction((Computable<List<ChainInfo>>) () -> {
            if (project.isDisposed()) { // 再次检查项目状态
                return Collections.emptyList();
            }
            // 在读操作内部再次检查 dumb mode。
            if (DumbService.getInstance(project).isDumb()) {
                LOG.info("在为LiteFlow chain调度读操作期间，项目进入了 dumb mode。正在跳过。");
                return Collections.emptyList();
            }

            List<ChainInfo> chainInfos = new ArrayList<>();
            // FileTypeIndex.getFiles 在 dumb mode 下对于“最新”结果应该是安全的，
            // 但后续的PSI操作（如解析标签）可能不是。
            // 总体的 dumb mode 检查是主要的防护。
            Collection<VirtualFile> virtualFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project));
            PsiManager psiManager = PsiManager.getInstance(project);

            for (VirtualFile virtualFile : virtualFiles) {
                if (project.isDisposed()) {
                    return Collections.emptyList();
                }
                PsiFile psiFile = psiManager.findFile(virtualFile);
                if (psiFile instanceof XmlFile) {
                    XmlFile xmlFile = (XmlFile) psiFile;
                    // [核心修改] 先使用 isLiteFlowXml 进行判断
                    if (LiteFlowXmlUtil.isLiteFlowXml(xmlFile)) {
                        // 判断通过后，可以安全地获取根标签进行处理
                        XmlTag flowRootTag = xmlFile.getDocument().getRootTag();
                        if (flowRootTag == null) {
                            continue; // 添加一个防御性检查
                        }

                        // <flow> 标签下的 <chain> 标签代表一个流程链
                        XmlTag[] chainTags = flowRootTag.findSubTags("chain");
                        for (XmlTag chainTag : chainTags) {
                            if (project.isDisposed()) { // 在循环中也检查项目状态
                                return Collections.emptyList();
                            }
                            String chainId = chainTag.getAttributeValue("id");
                            String chainName = chainTag.getAttributeValue("name"); // LiteFlow 中 chain 更常用 'name' 属性
                            String finalName = null;

                            // 优先使用 chain 的 'name' 属性作为显示名称，这是LiteFlow中更常见的做法
                            if (chainName != null && !chainName.trim().isEmpty()) {
                                finalName = chainName;
                            } else if (chainId != null && !chainId.trim().isEmpty()) {
                                finalName = chainId; // 如果 'name' 属性不存在，则使用 'id'
                            }

                            if (finalName != null) {
                                int offset = chainTag.getTextOffset(); // 获取标签在文件中的偏移量，用于导航
                                chainInfos.add(new ChainInfo(finalName, xmlFile, offset));
                            }
                        }
                    }
                }
            }
            return chainInfos;
        });
    }
}
