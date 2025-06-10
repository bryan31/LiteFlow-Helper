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
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 为 LiteFlow EL 表达式提供关键字代码补全功能。
 *
 * @author Bryan.Zhang
 */
public class LiteFlowElCompletionContributor extends CompletionContributor {

    public LiteFlowElCompletionContributor() {
        // 定义补全的触发位置：在名为 "chain" 的 XML 标签内部的文本区域
        // [修复] 使用 XmlPatterns.xmlTag() 替代已废弃的 PlatformPatterns.xmlTag()
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
                        // [修复] 使用 getLocalName 替代 getName 方法
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

                        // 添加所有 EL 关键字到补全列表
                        for (String keyword : LiteFlowXmlUtil.getElKeywords()) {
                            resultSet.addElement(
                                    LookupElementBuilder.create(keyword)
                                            .withBoldness(true) // 在补全列表中加粗显示
                                            .withTypeText("LiteFlow EL") // 在右侧显示类型提示
                            );
                        }
                    }
                }
        );
    }
}
