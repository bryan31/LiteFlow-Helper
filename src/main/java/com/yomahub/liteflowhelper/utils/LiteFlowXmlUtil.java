package com.yomahub.liteflowhelper.utils;

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

/**
 * LiteFlow XML 文件相关的工具类。
 */
public class LiteFlowXmlUtil {

    /**
     * 判断一个 XML 文件是否是 LiteFlow 的配置文件。
     * LiteFlow 配置文件的特征是：
     * 1. 根标签是 <flow>
     * 2. 必须包含至少一个 <chain> 标签 (根据用户之前的定义)
     *
     * @param xmlFile 要检查的 XmlFile 对象
     * @return 如果是 LiteFlow XML 文件则返回其根 <flow> 标签，否则返回 null。
     */
    @Nullable
    public static XmlTag getLiteFlowRootTag(@Nullable XmlFile xmlFile) {
        if (xmlFile == null) {
            return null;
        }
        XmlDocument document = xmlFile.getDocument();
        if (document == null) {
            return null;
        }
        XmlTag rootTag = document.getRootTag();
        // 检查根标签是否为 "flow" 并且至少包含一个 "chain" 子标签
        if (rootTag != null && "flow".equals(rootTag.getName()) && rootTag.findSubTags("chain").length > 0) {
            return rootTag;
        }
        return null;
    }

    /**
     * 从 LiteFlow 的根 <flow> 标签中获取 <nodes> 标签。
     *
     * @param flowRootTag LiteFlow XML 的根 <flow> 标签。如果为 null，则直接返回 null。
     * @return <nodes> 标签，如果不存在则返回 null。
     */
    @Nullable
    public static XmlTag getNodesTag(@Nullable XmlTag flowRootTag) {
        if (flowRootTag == null) {
            return null;
        }
        // 确保传入的确实是 <flow> 标签 (虽然调用方应该保证，但多一层校验无害)
        if ("flow".equals(flowRootTag.getName())) {
            return flowRootTag.findFirstSubTag("nodes");
        }
        return null; // 如果传入的不是 <flow> 标签
    }
}
