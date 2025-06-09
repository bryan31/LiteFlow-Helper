package com.yomahub.liteflowhelper.reference.config;

import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;

/**
 * 为 Spring 配置文件 (.properties, .yml) 中的 liteflow.ruleSource 属性提供引用支持。
 * <p>
 * 这个 Contributor 会在 IDEA 解析代码时被调用，如果发现符合条件的 PsiElement (即 liteflow.ruleSource 的键值对)，
 * 就会为其创建一个 {@link LiteFlowRuleSourceReference} 实例，从而启用 "Ctrl+Click" 或 "Cmd+Click" 跳转功能。
 * </p>
 *
 * @author Bryan.Zhang
 */
public class LiteFlowRuleSourceReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // [兼容性修正] 由于 PropertiesPatterns 在某些环境中不可用，我们回退到匹配所有 Property 元素，
        // 然后在 Provider 内部通过代码进行筛选。这具有更好的兼容性。
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(Property.class),
                new LiteFlowRuleSourceReferenceProvider()
        );

        // 注册针对 .yml/.yaml 文件的 Provider
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(YAMLKeyValue.class),
                new LiteFlowRuleSourceReferenceProvider()
        );
    }

    /**
     * 一个内部类，实现了 PsiReferenceProvider 接口。
     * 负责检查 PsiElement 是否是我们关心的目标，并为其创建引用。
     */
    private static class LiteFlowRuleSourceReferenceProvider extends PsiReferenceProvider {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
            String pathValue;
            PsiElement hostElement;
            TextRange rangeInHost;

            if (element instanceof Property) {
                // 处理 .properties 文件
                Property property = (Property) element;

                // [兼容性修正] 在 Provider 内部通过代码直接判断属性的 key 是否为 "liteflow.ruleSource"
                if (!"liteflow.ruleSource".equals(property.getKey())) {
                    return PsiReference.EMPTY_ARRAY;
                }

                pathValue = property.getValue();
                if (pathValue == null || pathValue.trim().isEmpty()) {
                    return PsiReference.EMPTY_ARRAY;
                }

                hostElement = property;
                rangeInHost = ElementManipulators.getValueTextRange(property);

            } else if (element instanceof YAMLKeyValue) {
                // 处理 .yml/.yaml 文件
                YAMLKeyValue keyValue = (YAMLKeyValue) element;

                if (!isLiteFlowRuleSourceProperty(keyValue)) {
                    return PsiReference.EMPTY_ARRAY;
                }

                if (keyValue.getValue() instanceof YAMLScalar) {
                    hostElement = keyValue.getValue();
                    pathValue = ((YAMLScalar) hostElement).getTextValue();
                    if (pathValue == null || pathValue.trim().isEmpty()) {
                        return PsiReference.EMPTY_ARRAY;
                    }
                    rangeInHost = ElementManipulators.getValueTextRange(hostElement);
                } else {
                    return PsiReference.EMPTY_ARRAY;
                }
            } else {
                return PsiReference.EMPTY_ARRAY;
            }

            // --- 后续通用处理逻辑 ---
            final String finalPathValue;

            // 处理 "classpath:" 前缀
            if (pathValue.startsWith("classpath:")) {
                finalPathValue = pathValue.substring("classpath:".length());
                // 调整文本范围，使其不包含 "classpath:"
                rangeInHost = new TextRange(rangeInHost.getStartOffset() + "classpath:".length(), rangeInHost.getEndOffset());
            } else {
                finalPathValue = pathValue;
            }

            // 确保文本范围有效
            if (rangeInHost.getStartOffset() > rangeInHost.getEndOffset()) {
                return PsiReference.EMPTY_ARRAY;
            }

            // 创建并返回我们的自定义引用
            return new PsiReference[]{new LiteFlowRuleSourceReference(hostElement, rangeInHost, finalPathValue)};
        }

        /**
         * 检查一个 YAMLKeyValue 元素是否对应 'liteflow.rule-source'。
         * 支持两种常见的 YAML 格式：
         * 1. 嵌套格式:
         * liteflow:
         * rule-source: ...
         * (或者 ruleSource: ...)
         * 2. 点分隔格式:
         * liteflow.rule-source: ...
         * (或者 liteflow.ruleSource: ...)
         */
        private boolean isLiteFlowRuleSourceProperty(YAMLKeyValue keyValue) {
            // 检查嵌套格式
            if ("rule-source".equalsIgnoreCase(keyValue.getKeyText()) || "ruleSource".equalsIgnoreCase(keyValue.getKeyText())) {
                PsiElement parentMapping = keyValue.getParentMapping();
                if (parentMapping != null && parentMapping.getParent() instanceof YAMLKeyValue) {
                    YAMLKeyValue parentKv = (YAMLKeyValue) parentMapping.getParent();
                    return "liteflow".equalsIgnoreCase(parentKv.getKeyText());
                }
            }
            // 检查点分隔格式
            if ("liteflow.rule-source".equalsIgnoreCase(keyValue.getKeyText()) || "liteflow.ruleSource".equalsIgnoreCase(keyValue.getKeyText())) {
                return true;
            }
            return false;
        }
    }
}
