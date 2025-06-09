package com.yomahub.liteflowhelper.highlight;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.ql.util.express.ExpressRunner;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LiteFlow 规则链表达式注解器
 * <p>
 * 负责对 liteflow.xml 中 <chain> 标签内的表达式进行语法高亮。
 * 使用 QLExpress 进行变量提取和分析。
 *
 * @author Bryan.Zhang
 */
public class LiteFlowChainAnnotator implements Annotator {

    // 使用 QLExpress 的 ExpressRunner，声明为静态实例以提高性能
    private static final ExpressRunner EXPRESS_RUNNER = new ExpressRunner();

    // 预编译的正则表达式，用于查找子变量定义 (e.g., "sub = THEN(a,b)")
    private static final Pattern SUB_VAR_PATTERN = Pattern.compile("\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*=");


    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // 只处理 chain 标签
        if (!(element instanceof XmlTag) || !"chain".equals(((XmlTag) element).getName())) {
            return;
        }

        // 判断是否为 LiteFlow 配置文件
        if (!(element.getContainingFile() instanceof XmlFile) || !LiteFlowXmlUtil.isLiteFlowXml((XmlFile) element.getContainingFile())) {
            return;
        }

        Project project = element.getProject();
        XmlTag chainTag = (XmlTag) element;
        XmlTagValue value = chainTag.getValue();

        if (value == null || value.getText().trim().isEmpty()) {
            return;
        }

        String expressionText = value.getText();
        int valueOffset = value.getTextRange().getStartOffset();

        // 获取缓存服务
        LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(project);

        // 1. 查找表达式中定义的子变量
        Map<String, TextRange> subVariableDefs = findSubVariableDefinitions(expressionText, valueOffset);

        // 2. 使用QLExpress获取所有外部变量
        String[] outVarNames;
        try {
            outVarNames = EXPRESS_RUNNER.getOutVarNames(expressionText);
        } catch (Exception e) {
            // QLExpress解析失败，不进行高亮处理
            return;
        }

        // 3. 遍历所有 QLExpress 识别出的变量，并进行高亮
        for (String varName : outVarNames) {
            // 使用正则表达式查找当前变量在表达式中的所有出现位置
            // \\b 是单词边界，确保不会匹配到 "a" 在 "abc" 中
            Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(varName) + "\\b");
            Matcher matcher = varPattern.matcher(expressionText);

            while (matcher.find()) {
                // 跳过关键字
                if (isKeyword(varName)) {
                    continue;
                }
                TextRange range = new TextRange(valueOffset + matcher.start(), valueOffset + matcher.end());

                // 确定高亮类型
                TextAttributesKey key;
                if (cacheService.containsCachedNode(varName)) {
                    // 是已定义的组件
                    key = LiteFlowHighlightColorSettings.COMPONENT_KEY;
                } else if (cacheService.containsCachedChain(varName)) {
                    // 是已定义的子流程
                    key = LiteFlowHighlightColorSettings.CHAIN_KEY;
                } else if (subVariableDefs.containsKey(varName)) {
                    // 是在此chain中定义的子变量
                    key = LiteFlowHighlightColorSettings.SUB_VARIABLE_KEY;
                } else {
                    // 未知的组件/变量
                    key = LiteFlowHighlightColorSettings.UNKNOWN_COMPONENT_KEY;
                }

                // 应用高亮
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(range)
                        .textAttributes(key)
                        .create();
            }
        }

        // 4. 对子变量的定义处进行高亮
        for (TextRange defRange : subVariableDefs.values()) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(defRange)
                    .textAttributes(LiteFlowHighlightColorSettings.SUB_VARIABLE_KEY)
                    .create();
        }
    }

    /**
     * 在表达式中查找所有子变量的定义。
     * @param expressionText 表达式字符串
     * @param baseOffset     表达式在文件中的起始偏移量
     * @return 一个映射，key是变量名，value是其在文件中的TextRange
     */
    private Map<String, TextRange> findSubVariableDefinitions(String expressionText, int baseOffset) {
        Map<String, TextRange> defs = new HashMap<>();
        // 使用分号分割表达式
        String[] statements = expressionText.split(";");
        int currentOffset = 0;
        for (String statement : statements) {
            Matcher matcher = SUB_VAR_PATTERN.matcher(statement);
            // 如果语句包含 "="，则认为是定义
            if (matcher.find()) {
                String varName = matcher.group(1);
                int varStartInStatement = matcher.start(1);
                int varEndInStatement = matcher.end(1);

                // 计算在整个expressionText中的绝对位置
                int defStart = currentOffset + varStartInStatement;
                int defEnd = currentOffset + varEndInStatement;

                // 转换为文件中的绝对TextRange
                TextRange rangeInFile = new TextRange(baseOffset + defStart, baseOffset + defEnd);
                defs.put(varName, rangeInFile);
            }
            // 累加偏移量，注意要加上分号的长度
            currentOffset += statement.length() + 1;
        }
        return defs;
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
            case "NODE":
            case "TRUE":
            case "FALSE":
                return true;
            default:
                return false;
        }
    }
}
