package com.yomahub.liteflowhelper.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.model.NodeCategory;
import com.yomahub.liteflowhelper.toolwindow.model.NodeType;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * LiteFlow 元素解析器：把 EL 中的标识符解析为节点 / 子流程定义。
 * <p>
 * 优先使用"当前文件"的实时 PSI（覆盖同文件未保存的改动），缺失时回退到 {@link LiteFlowCacheService} 的全局缓存。
 * 由 Annotator / Inspection / Reference / Completion 在各自上下文按需创建，
 * 一轮 pass 内复用以避免重复扫描当前文件。
 * </p>
 * <p>
 * 这是修复"未保存改动不生效"的关键：Annotator 运行在已提交的当前文件 PSI 之上，
 * 本类读取该 PSI 中的 {@code <chain>}/{@code <node>} 定义，因此同文件内的增删改可立即被识别，
 * 无需等待缓存（依赖落盘 + 防抖）刷新。
 * </p>
 */
public class LiteFlowElementResolver {

    private final LiteFlowCacheService cache;
    private final Map<String, XmlTag> liveNodes;   // 当前文件 <node id=...> 的实时索引
    private final Map<String, XmlTag> liveChains;  // 当前文件 <chain name/id=...> 的实时索引

    private LiteFlowElementResolver(LiteFlowCacheService cache,
                                    Map<String, XmlTag> liveNodes,
                                    Map<String, XmlTag> liveChains) {
        this.cache = cache;
        this.liveNodes = liveNodes;
        this.liveChains = liveChains;
    }

    /**
     * 基于当前文件构建解析器：扫描当前文件的 {@code <chain>}/{@code <node>} 定义形成实时索引，
     * 并持有全局缓存作为回退。
     */
    public static @NotNull LiteFlowElementResolver create(@NotNull Project project, @Nullable PsiFile currentFile) {
        LiteFlowCacheService cache = LiteFlowCacheService.getInstance(project);
        Map<String, XmlTag> nodes = new HashMap<>();
        Map<String, XmlTag> chains = new HashMap<>();

        if (currentFile instanceof XmlFile && LiteFlowXmlUtil.isLiteFlowXml((XmlFile) currentFile)) {
            XmlTag root = ((XmlFile) currentFile).getDocument() != null
                    ? ((XmlFile) currentFile).getDocument().getRootTag() : null;
            if (root != null) {
                for (XmlTag chainTag : root.findSubTags("chain")) {
                    String name = chainTag.getAttributeValue("name");
                    if (StringUtil.isEmpty(name)) {
                        name = chainTag.getAttributeValue("id");
                    }
                    if (!StringUtil.isEmpty(name)) {
                        chains.putIfAbsent(name, chainTag);
                    }
                }
                XmlTag nodesTag = LiteFlowXmlUtil.getNodesTag(root);
                if (nodesTag != null) {
                    for (XmlTag nodeTag : nodesTag.findSubTags("node")) {
                        String id = nodeTag.getAttributeValue("id");
                        if (!StringUtil.isEmpty(id)) {
                            nodes.putIfAbsent(id, nodeTag);
                        }
                    }
                }
            }
        }
        return new LiteFlowElementResolver(cache, nodes, chains);
    }

    public boolean isNode(@Nullable String name) {
        if (StringUtil.isEmpty(name)) return false;
        return liveNodes.containsKey(name) || cache.containsCachedNode(name);
    }

    public boolean isChain(@Nullable String name) {
        if (StringUtil.isEmpty(name)) return false;
        return liveChains.containsKey(name) || cache.containsCachedChain(name);
    }

    /**
     * 获取某名称对应节点的粗粒度类型类别（用于 EL 语义校验）。
     * 优先当前文件实时定义的 XML 节点，回退全局缓存；非节点/未知返回 null。
     */
    public @Nullable NodeCategory getNodeCategory(@Nullable String name) {
        if (StringUtil.isEmpty(name)) return null;
        XmlTag liveNode = liveNodes.get(name);
        if (liveNode != null && liveNode.isValid()) {
            return NodeType.fromXmlType(liveNode.getAttributeValue("type")).toCategory();
        }
        LiteFlowNodeInfo info = cache.getCachedNode(name);
        return info != null ? info.getType().toCategory() : null;
    }

    public @Nullable PsiElement resolveNode(@Nullable String name) {
        if (StringUtil.isEmpty(name)) return null;
        XmlTag live = liveNodes.get(name);
        if (live != null && live.isValid()) return live;
        Optional<LiteFlowNodeInfo> opt = cache.getCachedNodes().stream()
                .filter(n -> name.equals(n.getNodeId())).findFirst();
        return opt.map(LiteFlowNodeInfo::getPsiElement).orElse(null);
    }

    public @Nullable PsiElement resolveChain(@Nullable String name) {
        if (StringUtil.isEmpty(name)) return null;
        XmlTag live = liveChains.get(name);
        if (live != null && live.isValid()) return live;
        Optional<ChainInfo> opt = cache.getCachedChains().stream()
                .filter(c -> name.equals(c.getName())).findFirst();
        if (opt.isEmpty()) return null;
        ChainInfo info = opt.get();
        PsiFile file = info.getPsiFile();
        if (!file.isValid()) return null;
        PsiElement at = file.findElementAt(info.getOffset());
        if (at == null) return null;
        XmlTag tag = PsiTreeUtil.getParentOfType(at, XmlTag.class, false);
        if (tag != null && tag.getTextOffset() == info.getOffset() && "chain".equals(tag.getName())) {
            return tag;
        }
        return at;
    }

    /** 当前文件实时定义的节点 id 集合（用于补全等需要列举候选的场景）。 */
    public Set<String> liveNodeIds() {
        return liveNodes.keySet();
    }

    /** 当前文件实时定义的 chain 名称集合。 */
    public Set<String> liveChainNames() {
        return liveChains.keySet();
    }
}
