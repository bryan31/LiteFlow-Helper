package com.yomahub.liteflowhelper.highlight;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.ql.util.express.ExpressRunner;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LiteFlow 规则文件(XML)中 chain 的 EL 表达式注解器
 * <p>
 * 使用 QLExpress 解析 EL 表达式，替代原有的正则表达式方式，以更精确地识别变量。
 * <p>
 * 主要职责:
 * 1. 静态初始化一个 ExpressRunner 实例，避免重复创建。
 * 2. 识别 EL 表达式中的所有变量。
 * 3. 根据变量类型进行高亮：
 * - 组件 (Component): 存在于 cachedNodes 中。
 * - 子流程 (Sub-chain): 存在于 cachedChains 中。
 * - 子变量 (Sub-variable): 在当前 EL 中先定义后使用。
 * - 错误 (Error): 不属于以上任何类型。
 * 4. 高亮逻辑与导航逻辑分离，本类只负责视觉高亮。导航功能 (Ctrl+Click) 应由 PsiReferenceContributor 实现。
 *
 * @author Gemini
 * @since 2024-06-09
 */
public class LiteFlowChainAnnotator implements Annotator {

    // QLExpress 的执行器，静态初始化以提高性能
    private static final ExpressRunner EXPRESS_RUNNER = new ExpressRunner(false, false);

    // 匹配子变量定义的正则表达式, 例如: sub = THEN(a,b)
    private static final Pattern SUB_VARIABLE_DEFINITION_PATTERN = Pattern.compile("^\\s*([a-zA-Z0-9_]+)\\s*=");

    // 定义各种类型的高亮 Key. 建议将这些 Key 移至 LiteFlowHighlightColorSettings 类中统一管理
    // 组件高亮: #78ccf0, 加粗
    private static final TextAttributesKey LITEFLOW_COMPONENT_KEY = TextAttributesKey.createTextAttributesKey(
            "LITEFLOW_COMPONENT",
            new TextAttributes(new Color(0x78, 0xcc, 0xf0), null, null, null, Font.BOLD)
    );

    // 子流程高亮: #3d8beb, 加粗
    private static final TextAttributesKey LITEFLOW_CHAIN_KEY = TextAttributesKey.createTextAttributesKey(
            "LITEFLOW_CHAIN",
            new TextAttributes(new Color(0x3d, 0x8b, 0xeb), null, null, null, Font.BOLD)
    );

    // 子变量高亮: #40BF77, 加粗
    private static final TextAttributesKey LITEFLOW_SUB_VARIABLE_KEY = TextAttributesKey.createTextAttributesKey(
            "LITEFLOW_SUB_VARIABLE",
            new TextAttributes(new Color(0x40, 0xBF, 0x77), null, null, null, Font.BOLD)
    );

    // 错误/未定义组件高亮: #e77f7f, 波浪下划线
    private static final TextAttributesKey LITEFLOW_ERROR_KEY = TextAttributesKey.createTextAttributesKey(
            "LITEFLOW_ERROR",
            new TextAttributes(null, null, new Color(231, 127, 127), EffectType.WAVE_UNDERSCORE, Font.PLAIN)
    );


    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // 只处理 XML 的属性值
        if (!(element instanceof XmlAttributeValue)) {
            return;
        }

        Project project = element.getProject();
        PsiFile containingFile = element.getContainingFile();

        // 判断当前文件是否为 LiteFlow 配置文件
        if (!(containingFile instanceof XmlFile) || !LiteFlowXmlUtil.isLiteFlowXml((XmlFile) containingFile)) {
            return;
        }

        // **已修复**：获取属性所在的标签。XmlAttributeValue的父级是XmlAttribute，再上一级才是XmlTag。
        PsiElement parentTag = element.getParent().getParent();
        if (!(parentTag instanceof XmlTag) || !"chain".equals(((XmlTag) parentTag).getName())) {
            return;
        }

        // 获取 EL 表达式的文本内容 (去除首尾引号)
        String elScript = StringUtil.unquoteString(element.getText());
        if (StringUtil.isEmpty(elScript)) {
            return;
        }

        // 获取服务和缓存
        LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(project);

        // 步骤 1: 查找所有在当前EL中定义的子变量
        Set<String> subVariableDefinitions = findSubVariableDefinitions(elScript);

        // 步骤 2: 逐个语句解析和注解
        // EL 表达式通过分号分割
        String[] statements = elScript.split(";");
        int statementStartIndex = 0;

        // 获取 XML 属性值在文件中的起始偏移量 (跳过引号)
        int elementTextOffset = element.getTextRange().getStartOffset() + 1;

        for (String statement : statements) {
            if (StringUtil.isEmpty(statement)) {
                statementStartIndex += statement.length() + 1;
                continue;
            }

            try {
                // 使用 QLExpress 获取当前语句中用到的所有外部变量
                String[] varsInStatement = EXPRESS_RUNNER.getOutVarNames(statement);
                Set<String> uniqueVars = new HashSet<>(Arrays.asList(varsInStatement));

                for (String varName : uniqueVars) {
                    // 对于每个变量，查找其在当前语句中的所有出现位置
                    Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(varName) + "\\b");
                    Matcher matcher = varPattern.matcher(statement);

                    while (matcher.find()) {
                        // 计算变量在整个文件中的绝对文本范围
                        int start = elementTextOffset + statementStartIndex + matcher.start();
                        int end = elementTextOffset + statementStartIndex + matcher.end();
                        TextRange varRange = new TextRange(start, end);

                        // 应用注解
                        applyAnnotationForVariable(holder, cacheService, varName, varRange, subVariableDefinitions);
                    }
                }
            } catch (Exception e) {
                // QLExpress 解析异常，说明 EL 表达式可能存在语法错误
                // 此处可以添加日志记录，例如：
                // LOG.warn("QLExpress parsing error in statement: " + statement, e);
            }

            // 更新下一个语句的起始索引
            statementStartIndex += statement.length() + 1;
        }
    }

    /**
     * 查找 EL 脚本中定义的所有子变量名称
     *
     * @param elScript EL 脚本内容
     * @return 包含所有子变量名称的 Set
     */
    private Set<String> findSubVariableDefinitions(String elScript) {
        Set<String> definitions = new HashSet<>();
        // 按行分割来匹配定义
        String[] lines = elScript.split(";");
        for (String line : lines) {
            Matcher matcher = SUB_VARIABLE_DEFINITION_PATTERN.matcher(line.trim());
            if (matcher.find()) {
                definitions.add(matcher.group(1));
            }
        }
        return definitions;
    }

    /**
     * 根据变量类型应用不同的高亮注解
     *
     * @param holder                 AnnotationHolder
     * @param cacheService           缓存服务
     * @param varName                变量名
     * @param range                  变量在文件中的文本范围
     * @param subVariableDefinitions 在 EL 中定义的子变量集合
     */
    private void applyAnnotationForVariable(
            @NotNull AnnotationHolder holder,
            @NotNull LiteFlowCacheService cacheService,
            @NotNull String varName,
            @NotNull TextRange range,
            @NotNull Set<String> subVariableDefinitions) {

        // **已修复**：使用正确的 `hasCachedNodes` 方法检查组件
        if (cacheService.hasCachedNodes(varName)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(range)
                    .textAttributes(LITEFLOW_COMPONENT_KEY)
                    .create();
            return;
        }

        // **已修复**：使用正确的 `hasCachedChain` 方法检查子流程
        if (cacheService.hasCachedChain(varName)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(range)
                    .textAttributes(LITEFLOW_CHAIN_KEY)
                    .create();
            return;
        }

        // 检查是否为子变量
        if (subVariableDefinitions.contains(varName)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(range)
                    .textAttributes(LITEFLOW_SUB_VARIABLE_KEY)
                    .create();
            return;
        }

        // 如果都不是，则认为是未定义的错误组件
        holder.newAnnotation(HighlightSeverity.ERROR, "未定义的组件或变量: " + varName)
                .range(range)
                .textAttributes(LITEFLOW_ERROR_KEY)
                .create();
    }
}
