package com.yomahub.liteflowhelper.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.ProcessingContext;
import com.ql.util.express.ExpressRunner;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
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
 * 为 LiteFlow XML 中 chain 表达式里的组件、子流程、子变量提供引用和跳转。
 *
 * @author Bryan.Zhang
 */
public class LiteFlowChainReferenceContributor extends PsiReferenceContributor {

    private static final ExpressRunner EXPRESS_RUNNER = new ExpressRunner();

    // 正则表达式，用于查找子变量定义 (e.g., "sub = THEN(a,b)")
    private static final Pattern SUB_VAR_DEFINITION_PATTERN = Pattern.compile("\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*=");

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

                        if (!"chain".equals(xmlTag.getName()) || !LiteFlowXmlUtil.isLiteFlowXml((XmlFile) xmlTag.getContainingFile())) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        XmlTagValue tagValue = xmlTag.getValue();
                        String text = tagValue.getText();
                        if (text == null || text.trim().isEmpty()) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        List<PsiReference> references = new ArrayList<>();
                        String[] varNames;
                        try {
                            varNames = EXPRESS_RUNNER.getOutVarNames(text);
                        } catch (Exception e) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // 计算 value 相对于整个 tag 的起始偏移
                        int valueStartOffsetInTag = tagValue.getTextRange().getStartOffset() - xmlTag.getTextRange().getStartOffset();

                        for (String varName : varNames) {
                            // [修改] 调用 LiteFlowXmlUtil 中的公共方法判断关键字
                            if (LiteFlowXmlUtil.isElKeyword(varName)) {
                                continue;
                            }

                            Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(varName) + "\\b");
                            Matcher matcher = varPattern.matcher(text);

                            while (matcher.find()) {
                                int start = matcher.start();
                                int end = matcher.end();
                                TextRange rangeInTag = new TextRange(valueStartOffsetInTag + start, valueStartOffsetInTag + end);
                                references.add(new LiteFlowElementReference(xmlTag, rangeInTag, varName));
                            }
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
            LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(getElement().getProject());

            // 1. 尝试解析为组件 (Node)
            Optional<LiteFlowNodeInfo> nodeInfoOpt = cacheService.getCachedNodes().stream()
                    .filter(node -> node.getNodeId().equals(elementName))
                    .findFirst();
            if (nodeInfoOpt.isPresent()) {
                return nodeInfoOpt.get().getPsiElement();
            }

            // 2. 尝试解析为子流程 (Chain)
            Optional<ChainInfo> chainInfoOpt = cacheService.getCachedChains().stream()
                    .filter(chain -> chain.getName().equals(elementName))
                    .findFirst();
            if (chainInfoOpt.isPresent()) {
                ChainInfo chainInfo = chainInfoOpt.get();
                PsiFile psiFile = chainInfo.getPsiFile();

                // 确保 psiFile 仍然有效
                if (!psiFile.isValid()) {
                    return null;
                }

                PsiElement elementAtOffset = psiFile.findElementAt(chainInfo.getOffset());
                if (elementAtOffset != null) {
                    // [修复] 使用 PsiTreeUtil 向上查找 XmlTag。这是更稳妥的方式，因为 findElementAt 可能返回一个 token（比如'<'）。
                    // 我们需要的是包含这个 token 的整个 <chain> 标签。
                    XmlTag tag = PsiTreeUtil.getParentOfType(elementAtOffset, XmlTag.class, false);

                    // 确保找到的标签是我们想要的那个（它的起始偏移量和我们缓存的一致）
                    if (tag != null && tag.getTextOffset() == chainInfo.getOffset() && "chain".equals(tag.getName())) {
                        return tag;
                    }
                }
                // 如果上面的逻辑找不到，回退到原始方式，虽然它可能不总是精确指向标签本身。
                return elementAtOffset;
            }

            // 3. 尝试解析为当前 <chain> 中定义的子变量
            XmlTag currentChainTag = (XmlTag) getElement();
            XmlTagValue value = currentChainTag.getValue();
            String expressionText = value.getText();
            int valueStartOffset = value.getTextRange().getStartOffset();

            String[] statements = expressionText.split(";");
            int currentOffset = 0;
            for (String statement : statements) {
                Matcher matcher = SUB_VAR_DEFINITION_PATTERN.matcher(statement);
                if (matcher.find()) {
                    String varName = matcher.group(1);
                    if (varName.equals(elementName)) {
                        int varStartInStatement = matcher.start(1);
                        // 找到定义位置的PsiElement并返回
                        return getElement().getContainingFile().findElementAt(valueStartOffset + currentOffset + varStartInStatement);
                    }
                }
                currentOffset += statement.length() + 1;
            }

            // 无法解析
            return null;
        }

        @NotNull
        @Override
        public Object[] getVariants() {
            // 这个方法用于代码补全，可以后续实现
            return EMPTY_ARRAY;
        }
    }
}
