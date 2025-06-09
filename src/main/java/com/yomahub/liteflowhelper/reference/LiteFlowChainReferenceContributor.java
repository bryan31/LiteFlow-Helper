package com.yomahub.liteflowhelper.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 为 LiteFlow XML 中 chain 表达式里的组件ID、子流程、子变量提供引用和跳转。
 *
 * @author Bryan.Zhang
 */
public class LiteFlowChainReferenceContributor extends PsiReferenceContributor {

    private static final ExpressRunner EXPRESS_RUNNER = new ExpressRunner();

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // 修正 #1: 直接匹配名为 "chain" 的 XmlTag，而不是使用不存在的 xmlTagValue()
        registrar.registerReferenceProvider(XmlPatterns.xmlTag().withName("chain"),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                        // 修正 #2: `element` 现在就是 XmlTag，不再需要 getParent()
                        XmlTag chainTag = (XmlTag) element;
                        XmlTagValue valueElement = chainTag.getValue();

                        // 确保 chain 标签内有文本值
                        if (valueElement == null) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        if (!LiteFlowXmlUtil.isLiteFlowXml((XmlFile) chainTag.getContainingFile())) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        String expressionText = valueElement.getText();
                        if (expressionText == null || expressionText.trim().isEmpty()) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // 获取缓存
                        LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(element.getProject());
                        Set<String> knownNodeIds = cacheService.getCachedNodes().stream()
                                .map(LiteFlowNodeInfo::getNodeId).collect(Collectors.toSet());
                        Set<String> knownChainNames = cacheService.getCachedChains().stream()
                                .map(ChainInfo::getName).collect(Collectors.toSet());

                        // 查找子变量定义
                        Set<String> subVariableNames = Arrays.stream(expressionText.split(";"))
                                .filter(s -> s.contains("=")).map(s -> s.split("=", 2)[0].trim())
                                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());

                        List<PsiReference> references = new ArrayList<>();

                        try {
                            String[] vars = EXPRESS_RUNNER.getOutVarNames(expressionText);
                            for (String varName : new HashSet<>(Arrays.asList(vars))) { //使用Set去重
                                if (isKeyword(varName)) {
                                    continue;
                                }

                                LiteFlowComponentReference.ReferenceType type;
                                if (knownNodeIds.contains(varName)) {
                                    type = LiteFlowComponentReference.ReferenceType.COMPONENT;
                                } else if (knownChainNames.contains(varName)) {
                                    type = LiteFlowComponentReference.ReferenceType.CHAIN;
                                } else if (subVariableNames.contains(varName)) {
                                    type = LiteFlowComponentReference.ReferenceType.SUB_VARIABLE;
                                } else {
                                    continue; // 不为错误组件创建引用，避免误报
                                }

                                // 计算 value 文本部分相对于整个 tag 的起始偏移量
                                int valueOffsetInTag = valueElement.getTextRange().getStartOffset() - chainTag.getTextRange().getStartOffset();

                                // 找到所有该变量出现的位置并创建引用
                                Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(varName) + "\\b");
                                Matcher matcher = varPattern.matcher(expressionText);
                                while (matcher.find()) {
                                    // 范围现在是相对于整个 chainTag 的
                                    TextRange rangeInTag = new TextRange(valueOffsetInTag + matcher.start(), valueOffsetInTag + matcher.end());
                                    // 修正 #3: 引用现在创建在 chainTag (一个合法的 PsiElement)上
                                    references.add(new LiteFlowComponentReference(chainTag, rangeInTag, varName, type));
                                }
                            }
                        } catch (Exception e) {
                            // 如果QLExpress解析异常（比如语法不完整），则不提供引用，避免报错
                        }

                        return references.toArray(new PsiReference[0]);
                    }
                });
    }

    private boolean isKeyword(String text) {
        if (text == null) {
            return false;
        }
        switch (text.toUpperCase()) {
            case "THEN": case "WHEN": case "SWITCH": case "IF": case "ELSE":
            case "ELIF": case "FOR": case "WHILE": case "BREAK": case "ITERATOR":
            case "CATCH": case "DO": case "TO": case "DEFAULT": case "AND":
            case "OR": case "NOT": case "FINALLY": case "PRE": case "NODE":
                return true;
            default:
                return false;
        }
    }

    /**
     * 一个统一的引用类，处理组件、子流程和子变量的跳转逻辑。
     */
    public static class LiteFlowComponentReference extends PsiReferenceBase<PsiElement> {
        private final String id;
        private final ReferenceType type;

        public enum ReferenceType {
            COMPONENT,
            CHAIN,
            SUB_VARIABLE
        }

        public LiteFlowComponentReference(@NotNull PsiElement element, TextRange textRange, String id, ReferenceType type) {
            super(element, textRange, false); // 'soft' 设置为 false 表示这是一个硬引用
            this.id = id;
            this.type = type;
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(getElement().getProject());

            switch (type) {
                case COMPONENT:
                    // 跳转到组件定义（Java类或XML标签）
                    return cacheService.getCachedNodes().stream()
                            .filter(node -> node.getNodeId().equals(id))
                            .findFirst()
                            .map(LiteFlowNodeInfo::getPsiElement)
                            .orElse(null);
                case CHAIN:
                    // 跳转到子流程的 <chain> 标签
                    return cacheService.getCachedChains().stream()
                            .filter(chain -> chain.getName().equals(id))
                            .findFirst()
                            // 跳转到 chain 标签本身
                            .map(info -> {
                                PsiElement elementAt = info.getPsiFile().findElementAt(info.getOffset());
                                // findElementAt 可能返回一个文本节点，需要向上找到父标签
                                return (elementAt instanceof XmlTag) ? elementAt : elementAt.getParent();
                            })
                            .orElse(null);
                case SUB_VARIABLE:
                    // 对于子变量，我们跳转到其定义的语句。
                    XmlTag chainTag = (XmlTag) getElement();
                    XmlTagValue valueElement = chainTag.getValue();
                    if (valueElement == null) {
                        return null;
                    }

                    String expressionText = valueElement.getText();
                    // 匹配 "var" 后跟任意空格和 "="
                    Pattern defPattern = Pattern.compile("\\b" + Pattern.quote(id) + "\\s*=");
                    Matcher matcher = defPattern.matcher(expressionText);
                    if (matcher.find()) {
                        // 【最终修正】: 从引用的基础元素(getElement()即chainTag)获取文件
                        PsiFile containingFile = getElement().getContainingFile();
                        int absoluteOffset = valueElement.getTextRange().getStartOffset() + matcher.start();
                        return containingFile.findElementAt(absoluteOffset);
                    }
                    // 如果找不到定义（理论上不应该发生），则返回null
                    return null;
                default:
                    return null;
            }
        }

        /**
         * 为代码补全提供候选项。
         */
        @NotNull
        @Override
        public Object[] getVariants() {
            LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(getElement().getProject());
            List<String> variants = new ArrayList<>();
            // 添加所有已知的组件和链的ID作为候选项
            cacheService.getCachedNodes().forEach(node -> variants.add(node.getNodeId()));
            cacheService.getCachedChains().forEach(chain -> variants.add(chain.getName()));
            return variants.toArray();
        }
    }
}
