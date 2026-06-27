package com.yomahub.liteflowhelper.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.ProcessingContext;
import com.yomahub.liteflowhelper.service.LiteFlowElementResolver;
import com.yomahub.liteflowhelper.utils.LiteFlowElLexer;
import com.yomahub.liteflowhelper.utils.LiteFlowElToken;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 为 LiteFlow XML 中 chain 表达式里的组件、子流程、子变量提供引用和跳转。
 * <p>
 * 解析基于自写的 {@link LiteFlowElLexer}，不再依赖 QLExpress。
 * </p>
 *
 * @author Bryan.Zhang
 */
public class LiteFlowChainReferenceContributor extends PsiReferenceContributor {

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
                        if (!LiteFlowXmlUtil.isElCarrierTag(xmlTag)
                                || !LiteFlowXmlUtil.isLiteFlowXml((XmlFile) xmlTag.getContainingFile())) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        XmlTagValue tagValue = xmlTag.getValue();
                        String text = tagValue.getText();
                        if (text == null || text.trim().isEmpty()) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        List<PsiReference> references = new ArrayList<>();
                        List<LiteFlowElToken> tokens = LiteFlowElLexer.tokenize(text);

                        // value 相对于整个 tag 的起始偏移
                        int valueStartOffsetInTag = tagValue.getTextRange().getStartOffset() - xmlTag.getTextRange().getStartOffset();

                        for (LiteFlowElToken token : tokens) {
                            if (token.type != LiteFlowElToken.Type.IDENT) {
                                continue;
                            }
                            if (LiteFlowXmlUtil.isElKeyword(token.text)) {
                                continue;
                            }
                            TextRange rangeInTag = new TextRange(valueStartOffsetInTag + token.start, valueStartOffsetInTag + token.end);
                            references.add(new LiteFlowElementReference(xmlTag, rangeInTag, token.text));
                        }
                        return references.toArray(new PsiReference[0]);
                    }
                });
    }

    public static class LiteFlowElementReference extends PsiReferenceBase<PsiElement> {
        private final String elementName;

        public LiteFlowElementReference(@NotNull PsiElement element, TextRange textRange, String elementName) {
            super(element, textRange);
            this.elementName = elementName;
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            // 优先当前文件实时 PSI，回退全局缓存（节点 / 子流程）
            LiteFlowElementResolver resolver = LiteFlowElementResolver.create(getElement().getProject(), getElement().getContainingFile());

            PsiElement node = resolver.resolveNode(elementName);
            if (node != null) {
                return node;
            }
            PsiElement chain = resolver.resolveChain(elementName);
            if (chain != null) {
                return chain;
            }

            // 解析为当前 <chain> 中定义的子变量
            XmlTag currentChainTag = (XmlTag) getElement();
            XmlTagValue value = currentChainTag.getValue();
            String expressionText = value.getText();
            int valueStartOffset = value.getTextRange().getStartOffset();
            Map<String, LiteFlowElToken> subVarDefs = LiteFlowElLexer.findSubVarDefinitions(LiteFlowElLexer.tokenize(expressionText));
            LiteFlowElToken def = subVarDefs.get(elementName);
            if (def != null) {
                return getElement().getContainingFile().findElementAt(valueStartOffset + def.start);
            }

            return null;
        }

        @NotNull
        @Override
        public Object[] getVariants() {
            return EMPTY_ARRAY;
        }
    }
}
