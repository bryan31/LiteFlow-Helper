package com.yomahub.liteflowhelper.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import com.yomahub.liteflowhelper.icon.LiteFlowIcons;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

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
                        PsiElement parent = position.getParent();

                        // 向上查找，确保我们确实在 <chain> 标签内部
                        if (!(parent instanceof XmlTag) || !"chain".equals(((XmlTag) parent).getLocalName())) {
                            parent = parent.getParent();
                            if (!(parent instanceof XmlTag) || !"chain".equals(((XmlTag) parent).getLocalName())) {
                                return;
                            }
                        }

                        // 确保这是一个LiteFlow XML文件
                        if (!LiteFlowXmlUtil.isLiteFlowXml((XmlFile) position.getContainingFile())) {
                            return;
                        }

                        // 1. 添加所有 EL 关键字到补全列表
                        for (String keyword : LiteFlowXmlUtil.getElKeywords()) {
                            resultSet.addElement(
                                    LookupElementBuilder.create(keyword)
                                            .withIcon(LiteFlowIcons.EL_KEYWORD_ICON) // 设置关键字图标
                                            .withBoldness(true) // 在补全列表中加粗显示
                                            .withTypeText("LiteFlow EL", true) // 在右侧显示类型提示
                            );
                        }

                        // 获取缓存服务
                        LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(parameters.getEditor().getProject());
                        if(cacheService == null){
                            return;
                        }

                        // 2. 添加所有已缓存的组件 (Nodes) 到补全列表
                        for(LiteFlowNodeInfo node : cacheService.getCachedNodes()){
                            LookupElementBuilder element = LookupElementBuilder.create(node.getNodeId())
                                    .withIcon(node.getType().getIcon()) // 根据组件类型设置图标
                                    .withBoldness(true)
                                    .withTailText(" (" + node.getFileName() + ")", true) // 显示所在文件名
                                    .withTypeText(node.getType().getDescription(), true); // 显示组件类型描述
                            resultSet.addElement(element);
                        }


                        // 3. 添加所有已缓存的子流程 (Chains) 到补全列表
                        for(ChainInfo chain : cacheService.getCachedChains()){
                            // 避免将当前正在编辑的chain自身也添加到补全列表
                            if(parent.isEquivalentTo(chain.getPsiFile().findElementAt(chain.getOffset()).getParent())){
                                continue;
                            }

                            LookupElementBuilder element = LookupElementBuilder.create(chain.getName())
                                    .withIcon(LiteFlowIcons.CHAIN_ICON) // 设置Chain图标
                                    .withBoldness(true)
                                    .withTailText(" (" + chain.getFileName() + ")", true) // 显示所在文件名
                                    .withTypeText("Chain", true); // 类型为Chain
                            resultSet.addElement(element);
                        }

                    }
                }
        );
    }
}
