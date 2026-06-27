package com.yomahub.liteflowhelper.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import com.yomahub.liteflowhelper.icon.LiteFlowIcons;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.service.LiteFlowElementResolver;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * 为 LiteFlow EL 表达式提供关键字、组件、子流程的代码补全功能。
 *
 * @author Bryan.Zhang
 */
public class LiteFlowElCompletionContributor extends CompletionContributor {

    public LiteFlowElCompletionContributor() {
        // 定义补全的触发位置：在名为 "chain" 的 XML 标签内部的文本区域
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(XmlTokenType.XML_DATA_CHARACTERS)
                        .inside(XmlPatterns.xmlTag().withLocalName("chain")),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet resultSet) {

                        PsiElement position = parameters.getPosition();
                        // 找到光标所在的 EL 承载标签（直接值 chain / route / body）
                        XmlTag elTag = PsiTreeUtil.getParentOfType(position, XmlTag.class);
                        if (elTag == null || !LiteFlowXmlUtil.isElCarrierTag(elTag)) {
                            return;
                        }

                        // 确保这是一个LiteFlow XML文件
                        if (!LiteFlowXmlUtil.isLiteFlowXml((XmlFile) position.getContainingFile())) {
                            return;
                        }
                        // 当前所在的 chain 标签：直接值写法即 elTag；route/body 写法为其父
                        XmlTag parent = "chain".equals(elTag.getName()) ? elTag : (XmlTag) elTag.getParent();

                        // 判断光标是否紧跟 "."：点号后只补修饰符/续写，不补节点/链/关键字
                        boolean afterDot = isAfterDot(parameters, elTag);

                        // 1. 关键字（按上下文过滤）
                        for (String keyword : LiteFlowXmlUtil.getCompletionKeywords(afterDot)) {
                            resultSet.addElement(
                                    LookupElementBuilder.create(keyword)
                                            .withIcon(LiteFlowIcons.EL_KEYWORD_ICON) // 设置关键字图标
                                            .withBoldness(true) // 在补全列表中加粗显示
                                            .withTypeText(afterDot ? "EL 修饰符/续写" : "LiteFlow EL", true) // 类型提示
                            );
                        }

                        // 点号后只补修饰符/续写，不再补节点/链/子变量
                        if (afterDot) {
                            return;
                        }

                        // 获取缓存服务与解析器（解析器覆盖当前文件实时 PSI）
                        Project proj = parameters.getEditor().getProject();
                        LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(proj);
                        if (cacheService == null) {
                            return;
                        }
                        LiteFlowElementResolver resolver = LiteFlowElementResolver.create(proj, position.getContainingFile());

                        // 2. 添加所有已缓存的组件 (Nodes) 到补全列表
                        Set<String> addedNodeIds = new HashSet<>();
                        for (LiteFlowNodeInfo node : cacheService.getCachedNodes()) {
                            addedNodeIds.add(node.getNodeId());
                            LookupElementBuilder element = LookupElementBuilder.create(node.getNodeId())
                                    .withIcon(node.getType().getIcon()) // 根据组件类型设置图标
                                    .withBoldness(true)
                                    .withTailText(" (" + node.getFileName() + ")", true) // 显示所在文件名
                                    .withTypeText(node.getType().getDescription(), true); // 显示组件类型描述
                            resultSet.addElement(element);
                        }
                        // 补全当前文件实时定义、但尚未进入缓存的节点（同文件未保存的新增）
                        for (String liveNodeId : resolver.liveNodeIds()) {
                            if (addedNodeIds.add(liveNodeId)) {
                                resultSet.addElement(LookupElementBuilder.create(liveNodeId)
                                        .withIcon(LiteFlowIcons.COMMON_COMPONENT_ICON)
                                        .withBoldness(true)
                                        .withTypeText("Node", true));
                            }
                        }

                        // 3. 添加所有已缓存的子流程 (Chains) 到补全列表
                        Set<String> addedChainNames = new HashSet<>();
                        for (ChainInfo chain : cacheService.getCachedChains()) {
                            // 避免将当前正在编辑的chain自身也添加到补全列表
                            if (parent.isEquivalentTo(chain.getPsiFile().findElementAt(chain.getOffset()).getParent())) {
                                addedChainNames.add(chain.getName()); // 自身也要记入，避免下面重复添加
                                continue;
                            }
                            addedChainNames.add(chain.getName());
                            LookupElementBuilder element = LookupElementBuilder.create(chain.getName())
                                    .withIcon(LiteFlowIcons.CHAIN_ICON) // 设置Chain图标
                                    .withBoldness(true)
                                    .withTailText(" (" + chain.getFileName() + ")", true) // 显示所在文件名
                                    .withTypeText("Chain", true); // 类型为Chain
                            resultSet.addElement(element);
                        }
                        // 补全当前文件实时定义、但尚未进入缓存的 chain
                        for (String liveChain : resolver.liveChainNames()) {
                            if (addedChainNames.add(liveChain)) {
                                resultSet.addElement(LookupElementBuilder.create(liveChain)
                                        .withIcon(LiteFlowIcons.CHAIN_ICON)
                                        .withBoldness(true)
                                        .withTypeText("Chain", true));
                            }
                        }

                    }
                }
        );
    }

    /**
     * 判断补全位置是否紧跟一个 "."（即用户在 nodeX.| 或 THEN(...).| 处补全）。
     * 向前跳过正在输入的标识符和空白，若碰到 "." 则视为点号后补全。
     */
    private static boolean isAfterDot(@NotNull CompletionParameters parameters, @NotNull XmlTag elTag) {
        com.intellij.psi.xml.XmlTagValue value = elTag.getValue();
        if (value == null) {
            return false;
        }
        int valueStart = value.getTextRange().getStartOffset();
        String text = value.getText();
        int rel = parameters.getOffset() - valueStart;
        if (rel <= 0 || rel > text.length()) {
            return false;
        }
        for (int i = rel - 1; i >= 0; i--) {
            char ch = text.charAt(i);
            if (ch == '.') {
                return true;
            }
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '_') {
                continue;
            }
            return false;
        }
        return false;
    }
}
