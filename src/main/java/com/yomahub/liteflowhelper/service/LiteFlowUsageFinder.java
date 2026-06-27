package com.yomahub.liteflowhelper.service;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.utils.LiteFlowElLexer;
import com.yomahub.liteflowhelper.utils.LiteFlowElToken;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 在所有已缓存 chain 中查找节点引用，支撑"反向导航 gutter 图标"与"未使用组件检查"。
 * <p>遍历 {@link LiteFlowCacheService} 中的 chain，读取其 EL 承载段（chain 直接值 / route / body）
 * 并分词，据此判断某 nodeId 是否被引用。</p>
 */
public final class LiteFlowUsageFinder {

    private LiteFlowUsageFinder() {
    }

    /**
     * 返回引用了指定 nodeId 的 chain 标签列表（用于 gutter 多目标导航）。
     */
    public static @NotNull List<XmlTag> findChainsReferencingNode(@NotNull Project project, @NotNull String nodeId) {
        List<XmlTag> result = new ArrayList<>();
        if (nodeId.isEmpty()) {
            return result;
        }
        LiteFlowCacheService cache = LiteFlowCacheService.getInstance(project);
        for (ChainInfo chain : cache.getCachedChains()) {
            PsiElement chainTag = resolveChainTag(chain);
            if (chainTag instanceof XmlTag && chainReferencesNode((XmlTag) chainTag, nodeId)) {
                result.add((XmlTag) chainTag);
            }
        }
        return result;
    }

    /**
     * 收集所有 chain 的 EL 中出现过的（非关键字）标识符集合，用于 O(1) 判断某 nodeId 是否被引用。
     */
    public static @NotNull Set<String> collectReferencedNodeIds(@NotNull Project project) {
        Set<String> referenced = new HashSet<>();
        LiteFlowCacheService cache = LiteFlowCacheService.getInstance(project);
        for (ChainInfo chain : cache.getCachedChains()) {
            PsiElement chainTag = resolveChainTag(chain);
            if (!(chainTag instanceof XmlTag)) {
                continue;
            }
            for (XmlTag carrier : collectElCarrierTags((XmlTag) chainTag)) {
                XmlTagValue value = carrier.getValue();
                String text = value == null ? null : value.getText();
                if (text == null) {
                    continue;
                }
                for (LiteFlowElToken t : LiteFlowElLexer.tokenize(text)) {
                    if (t.type == LiteFlowElToken.Type.IDENT && !LiteFlowXmlUtil.isElKeyword(t.text)) {
                        referenced.add(t.text);
                    }
                }
            }
        }
        return referenced;
    }

    /** 根据 ChainInfo 缓存的偏移定位到当前的 <chain> 标签。 */
    private static PsiElement resolveChainTag(ChainInfo chain) {
        PsiFile file = chain.getPsiFile();
        if (file == null || !file.isValid()) {
            return null;
        }
        PsiElement at = file.findElementAt(chain.getOffset());
        if (at == null) {
            return null;
        }
        XmlTag tag = PsiTreeUtil.getParentOfType(at, XmlTag.class, false);
        if (tag != null && tag.getTextOffset() == chain.getOffset() && "chain".equals(tag.getName())) {
            return tag;
        }
        return tag; // 容错：尽力返回最近的 chain 标签
    }

    /** 一个 chain 的所有 EL 承载子标签：无 route/body 时为 chain 自身，否则为 route/body。 */
    private static List<XmlTag> collectElCarrierTags(XmlTag chainTag) {
        List<XmlTag> carriers = new ArrayList<>();
        XmlTag route = chainTag.findFirstSubTag("route");
        XmlTag body = chainTag.findFirstSubTag("body");
        if (route == null && body == null) {
            carriers.add(chainTag);
        } else {
            if (route != null) {
                carriers.add(route);
            }
            if (body != null) {
                carriers.add(body);
            }
        }
        return carriers;
    }

    private static boolean chainReferencesNode(XmlTag chainTag, String nodeId) {
        for (XmlTag carrier : collectElCarrierTags(chainTag)) {
            XmlTagValue value = carrier.getValue();
            String text = value == null ? null : value.getText();
            if (text == null) {
                continue;
            }
            for (LiteFlowElToken t : LiteFlowElLexer.tokenize(text)) {
                if (t.type == LiteFlowElToken.Type.IDENT
                        && !LiteFlowXmlUtil.isElKeyword(t.text)
                        && nodeId.equals(t.text)) {
                    return true;
                }
            }
        }
        return false;
    }
}
