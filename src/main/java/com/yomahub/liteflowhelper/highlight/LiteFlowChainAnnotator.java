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
import com.ql.util.express.ExpressRunner;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LiteFlow 规则链表达式注解器
 * <p>
 * 负责对 liteflow.xml 中 <chain> 标签内的表达式进行语法高亮。
 * 使用 QLExpress 进行变量识别，以提高准确性。
 *
 * @author Bryan.Zhang
 */
public class LiteFlowChainAnnotator implements Annotator {

    // 使用 QLExpress runner 来解析表达式中的变量，应只实例化一次
    private static final ExpressRunner EXPRESS_RUNNER = new ExpressRunner();

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // 注解器会被频繁调用，需要精确地找到目标 PsiElement
        // 目标是 <chain> 标签内的文本内容，即 XmlTagValue
        if (!(element instanceof XmlTagValue)) {
            return;
        }

        // 确认父标签是 <chain>
        PsiElement parent = element.getParent();
        if (!(parent instanceof XmlTag) || !"chain".equals(((XmlTag) parent).getName())) {
            return;
        }

        Project project = element.getProject();
        XmlFile xmlFile = (XmlFile) element.getContainingFile();

        // 判断是否为 LiteFlow 配置文件
        if (!LiteFlowXmlUtil.isLiteFlowXml(xmlFile)) {
            return;
        }

        XmlTagValue value = (XmlTagValue) element;
        String expressionText = value.getText();
        if (expressionText == null || expressionText.trim().isEmpty()) {
            return;
        }

        // 获取缓存的节点和链信息
        LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(project);
        Set<String> knownNodeIds = cacheService.getCachedNodes().stream()
                .map(LiteFlowNodeInfo::getNodeId)
                .collect(Collectors.toSet());
        Set<String> knownChainNames = cacheService.getCachedChains().stream()
                .map(ChainInfo::getName)
                .collect(Collectors.toSet());

        // 1. 预处理，找出所有在当前表达式中定义的子变量
        Set<String> subVariableNames = Arrays.stream(expressionText.split(";"))
                .map(String::trim)
                .filter(s -> s.contains("="))
                .map(s -> s.split("=", 2)[0].trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        // 2. 使用 QLExpress 获取所有外部变量
        try {
            String[] vars = EXPRESS_RUNNER.getOutVarNames(expressionText);
            Set<String> allReferencedVars = new HashSet<>(Arrays.asList(vars));

            // 3. 遍历所有QLExpress识别出的变量，并在文本中找到它们，然后应用高亮
            for (String varName : allReferencedVars) {
                // 排除关键字
                if (isKeyword(varName)) {
                    continue;
                }

                // 使用正则表达式匹配变量，确保是全词匹配
                Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(varName) + "\\b");
                Matcher matcher = varPattern.matcher(expressionText);

                while (matcher.find()) {
                    // 计算变量在文件中的绝对范围
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

                    // 创建注解
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
        } catch (Exception e) {
            // 如果 QLExpress 解析失败（例如，表达式语法不完整），可以选择降级为旧版正则表达式，或忽略
            // 为避免在用户输入过程中显示错误的告警，此处选择静默失败
        }
    }


    /**
     * 判断字符串是否为LiteFlow的EL关键字，避免被错误高亮。
     * @param text 待判断的文本
     * @return 如果是关键字则返回true
     */
    private boolean isKeyword(String text) {
        if (text == null) return false;
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
