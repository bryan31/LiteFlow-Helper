package com.yomahub.liteflowhelper.toolwindow.model;

import com.intellij.psi.PsiElement; // 使用 PsiElement 以获得更大的灵活性 (可以是类或XML标签)
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 用于存储已发现的 LiteFlow 节点的信息。
 */
public class LiteFlowNodeInfo {
    private final String nodeId;        // 节点的ID (例如：注解中的value，XML中的id)
    private final String nodeName;      // 节点的名称 (可选, 通常来自XML的'name'属性)
    private final NodeType type;        // 节点的类型 (例如：普通组件、选择脚本等)
    private final PsiElement psiElement; // 指向该节点定义的 PsiElement (用于导航，可以是 PsiClass 或 XmlTag)
    private final String fileName;      // 节点所在的文件名
    private final String source;        // 节点来源 ("Java类" 或 "XML脚本")

    public LiteFlowNodeInfo(@NotNull String nodeId,
                            @Nullable String nodeName,
                            @NotNull NodeType type,
                            @NotNull PsiElement psiElement,
                            @NotNull String source) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.type = type;
        this.psiElement = psiElement;
        this.fileName = psiElement.getContainingFile().getName(); // 获取所在文件名
        this.source = source;
    }

    @NotNull
    public String getNodeId() {
        return nodeId;
    }

    @Nullable
    public String getNodeName() {
        return nodeName;
    }

    @NotNull
    public NodeType getType() {
        return type;
    }

    @NotNull
    public PsiElement getPsiElement() {
        return psiElement;
    }

    @NotNull
    public PsiFile getPsiFile() {
        return psiElement.getContainingFile();
    }

    public int getOffset() {
        return psiElement.getTextOffset(); // 获取元素在文件中的偏移量
    }

    @NotNull
    public String getFileName() {
        return fileName;
    }

    @NotNull
    public String getSource() {
        return source;
    }

    /**
     * 重写 toString 方法，用于在树节点中更好地显示节点信息。
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(nodeId);
        // 如果 nodeName 存在且与 nodeId 不同，则显示 nodeName
        if (nodeName != null && !nodeName.isEmpty() && !nodeName.equals(nodeId)) {
            sb.append(" [").append(nodeName).append("]");
        }
        // 添加类型描述、来源和文件名
        sb.append(" (").append(type.getDescription())
                .append(" - ").append(source) // 显示来源
                .append(" - ").append(fileName).append(")");
        return sb.toString();
    }
}
