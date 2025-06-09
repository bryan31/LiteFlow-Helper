package com.yomahub.liteflowhelper.highlight;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;
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

    /**
     * 使用正则表达式来查找所有潜在的组件/流程标识符。
     * 这个正则表达式匹配所有符合Java变量命名规则的 "单词"。
     * \b 是单词边界，确保我们不会匹配到单词的一部分。
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // 【最终修复】直接检查元素是否为我们关心的 <chain> 标签本身
        if (!(element instanceof XmlTag)) {
            return;
        }

        XmlTag chainTag = (XmlTag) element;
        if (!"chain".equals(chainTag.getName())) {
            return;
        }

        // 确认现在拿到了正确的 <chain> 标签，再获取其内容进行处理
        XmlTagValue value = chainTag.getValue();
        if (value == null) {
            return;
        }

        Project project = element.getProject();
        XmlFile xmlFile = (XmlFile) element.getContainingFile();

        if (!LiteFlowXmlUtil.isLiteFlowXml(xmlFile)) {
            return;
        }

        String expressionText = value.getText();
        if (expressionText == null || expressionText.trim().isEmpty()) {
            return;
        }

        LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(project);
        Set<String> knownNodeIds = cacheService.getCachedNodes().stream()
                .map(LiteFlowNodeInfo::getNodeId)
                .collect(Collectors.toSet());
        Set<String> knownChainNames = cacheService.getCachedChains().stream()
                .map(ChainInfo::getName)
                .collect(Collectors.toSet());

        Set<String> subVariableNames = Arrays.stream(expressionText.split(";"))
                .map(String::trim)
                .filter(s -> s.contains("="))
                .map(s -> s.split("=", 2)[0].trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        Matcher matcher = IDENTIFIER_PATTERN.matcher(expressionText);

        while (matcher.find()) {
            String varName = matcher.group();

            if (isKeyword(varName)) {
                continue;
            }

            // 计算变量在文件中的绝对范围
            // value.getTextRange() 获取的是 <chain> 标签 *内部* 内容的范围
            TextRange range = new TextRange(value.getTextRange().getStartOffset() + matcher.start(), value.getTextRange().getStartOffset() + matcher.end());

            TextAttributesKey key;
            if (knownNodeIds.contains(varName)) {
                key = LiteFlowHighlightColorSettings.COMPONENT_KEY;
            } else if (knownChainNames.contains(varName)) {
                key = LiteFlowHighlightColorSettings.SUB_CHAIN_KEY;
            } else if (subVariableNames.contains(varName)) {
                key = LiteFlowHighlightColorSettings.SUB_VARIABLE_KEY;
            } else {
                key = LiteFlowHighlightColorSettings.ERROR_KEY;
            }

            AnnotationBuilder builder;
            if (key == LiteFlowHighlightColorSettings.ERROR_KEY) {
                builder = holder.newAnnotation(HighlightSeverity.ERROR, "未定义的组件、流程或变量: " + varName)
                        .range(range);
            } else {
                builder = holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(range);
            }
            builder.textAttributes(key).create();
        }
    }


    /**
     * 判断字符串是否为LiteFlow的EL关键字，避免被错误高亮。
     * @param text 待判断的文本
     * @return 如果是关键字则返回true
     */
    private boolean isKeyword(String text) {
        if (text == null) {
            return false;
        }
        // 关键字不区分大小写
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
            case "NODE": // node(...) 中的 node
                return true;
            default:
                return false;
        }
    }
}
