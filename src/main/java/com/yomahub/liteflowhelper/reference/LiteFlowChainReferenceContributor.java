package com.yomahub.liteflowhelper.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.ProcessingContext;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为 LiteFlow XML 中 chain 表达式里的组件ID提供引用。
 *
 * @author Bryan.Zhang
 */
public class LiteFlowChainReferenceContributor extends PsiReferenceContributor {

    // 正则表达式，与 Annotator 中的保持一致
    private static final Pattern COMPONENT_ID_PATTERN = Pattern.compile("node\\(\"([a-zA-Z0-9_\\-]+)\"\\)|([a-zA-Z][a-zA-Z0-9_\\-]*)");

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlTag.class),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                        if (!(element instanceof XmlTag)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        XmlTag xmlTag = (XmlTag) element;

                        // 确保是 LiteFlow XML 中的 chain 标签
                        if (!"chain".equals(xmlTag.getName()) || !LiteFlowXmlUtil.isLiteFlowXml((XmlFile) xmlTag.getContainingFile())) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        XmlTagValue tagValue = xmlTag.getValue();
                        String text = tagValue.getText();
                        if (text == null || text.trim().isEmpty()) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        Matcher matcher = COMPONENT_ID_PATTERN.matcher(text);
                        List<PsiReference> references = new ArrayList<>();

                        // 计算 value 相对于整个 tag 的起始偏移
                        int valueStartOffsetInTag = tagValue.getTextRange().getStartOffset() - xmlTag.getTextRange().getStartOffset();

                        while (matcher.find()) {
                            String componentId;
                            int start;
                            int end;

                            if (matcher.group(1) != null) {
                                componentId = matcher.group(1);
                                start = matcher.start(1);
                                end = matcher.end(1);
                            } else if (matcher.group(2) != null) {
                                componentId = matcher.group(2);
                                if (isKeyword(componentId)) continue;
                                start = matcher.start(2);
                                end = matcher.end(2);
                            } else {
                                continue;
                            }

                            // 关键修正: 创建的引用必须基于调用者(element, 即xmlTag)
                            // TextRange 的偏移量也必须是相对于 xmlTag 的
                            TextRange rangeInTag = new TextRange(valueStartOffsetInTag + start, valueStartOffsetInTag + end);
                            references.add(new LiteFlowComponentReference(xmlTag, rangeInTag, componentId));
                        }
                        return references.toArray(new PsiReference[0]);
                    }
                });
    }

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
            case "node":
                return true;
            default:
                return false;
        }
    }

    public static class LiteFlowComponentReference extends PsiReferenceBase<PsiElement> {
        private final String componentId;

        public LiteFlowComponentReference(@NotNull PsiElement element, TextRange textRange, String componentId) {
            super(element, textRange);
            this.componentId = componentId;
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(getElement().getProject());
            List<LiteFlowNodeInfo> cachedNodes = cacheService.getCachedNodes();

            Optional<LiteFlowNodeInfo> nodeInfoOptional = cachedNodes.stream()
                    .filter(node -> node.getNodeId().equals(componentId))
                    .findFirst();

            return nodeInfoOptional.map(LiteFlowNodeInfo::getPsiElement).orElse(null);
        }

        @NotNull
        @Override
        public Object[] getVariants() {
            LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(getElement().getProject());
            return cacheService.getCachedNodes().stream().map(LiteFlowNodeInfo::getNodeId).toArray();
        }
    }
}
