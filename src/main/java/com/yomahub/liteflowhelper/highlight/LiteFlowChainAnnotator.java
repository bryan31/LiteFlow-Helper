package com.yomahub.liteflowhelper.highlight;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LiteFlow 规则链表达式注解器
 * <p>
 * 负责对 liteflow.xml 中 <chain> 标签内的表达式进行语法高亮。
 *
 * @author Bryan.Zhang
 */
public class LiteFlowChainAnnotator implements Annotator {

    // 正则表达式，用于匹配表达式中的组件ID
    // 这个表达式会匹配 node("...") 写法或者直接的组件ID
    private static final Pattern COMPONENT_ID_PATTERN = Pattern.compile("node\\(\"([a-zA-Z0-9_]+)\"\\)|([a-zA-Z][a-zA-Z0-9_]*)");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        Project project = element.getProject();

        // 仅处理 XML 文件
        if (!(element.getContainingFile() instanceof XmlFile)) {
            return;
        }

        XmlFile xmlFile = (XmlFile) element.getContainingFile();

        // 判断是否为 LiteFlow 配置文件
        if (!LiteFlowXmlUtil.isLiteFlowXml(xmlFile)) {
            return;
        }

        // 只处理 chain 标签
        if (element instanceof XmlTag && "chain".equals(((XmlTag) element).getName())) {
            XmlTag chainTag = (XmlTag) element;
            XmlTagValue value = chainTag.getValue();
            // 增加空值判断
            if (value == null || value.getText().trim().isEmpty()) {
                return;
            }
            String expressionText = value.getText();

            // 获取缓存的节点信息
            LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(project);
            List<String> knownNodeIds = cacheService.getCachedNodes().stream()
                    .map(LiteFlowNodeInfo::getNodeId)
                    .collect(Collectors.toList());

            // 使用正则表达式匹配表达式中的所有组件
            Matcher matcher = COMPONENT_ID_PATTERN.matcher(expressionText);
            int valueOffset = value.getTextRange().getStartOffset();

            while (matcher.find()) {
                String componentId;
                int start;
                int end;

                // 检查是 node("...") 形式还是直接的 ID
                if (matcher.group(1) != null) {
                    // 匹配到 node("...")
                    componentId = matcher.group(1);
                    start = matcher.start(1);
                    end = matcher.end(1);
                } else if (matcher.group(2) != null) {
                    // 匹配到直接的组件ID
                    componentId = matcher.group(2);
                    // 排除EL关键字
                    if (isKeyword(componentId)) {
                        continue;
                    }
                    start = matcher.start(2);
                    end = matcher.end(2);
                } else {
                    continue;
                }

                // 计算组件ID在文件中的绝对范围
                TextRange range = new TextRange(valueOffset + start, valueOffset + end);

                // 根据组件是否存在于缓存来应用不同的高亮
                if (knownNodeIds.contains(componentId)) {
                    // 存在，绿色粗体高亮
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(range)
                            .textAttributes(LiteFlowHighlightColorSettings.KNOWN_COMPONENT_KEY)
                            .create();
                } else {
                    // 不存在，红色粗体高亮
                    holder.newSilentAnnotation(HighlightSeverity.WARNING)
                            .range(range)
                            .textAttributes(LiteFlowHighlightColorSettings.UNKNOWN_COMPONENT_KEY)
                            .create();
                }
            }
        }
    }

    // 简单判断是否为LiteFlow的EL关键字，避免被错误高亮
    private boolean isKeyword(String text) {
        switch (text.toUpperCase()) {
            case "THEN":
            case "WHEN":
            case "SWITCH":
            case "IF":
            case "ELSE":
            case "ELIF":
            case "FOR":
            case "WHILE":
            case "BREAK":
            case "ITERATOR":
            case "CATCH":
            case "DO":
            case "TO":
            case "DEFAULT":
            case "AND":
            case "OR":
            case "NOT":
            case "FINALLY":
            case "PRE":
            case "node": // node(...) 中的 node
                return true;
            default:
                return false;
        }
    }
}
