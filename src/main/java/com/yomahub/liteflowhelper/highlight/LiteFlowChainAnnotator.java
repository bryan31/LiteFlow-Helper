package com.yomahub.liteflowhelper.highlight;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.yomahub.liteflowhelper.service.LiteFlowElementResolver;
import com.yomahub.liteflowhelper.utils.LiteFlowElLexer;
import com.yomahub.liteflowhelper.utils.LiteFlowElToken;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * LiteFlow 规则链表达式注解器。
 * <p>
 * 对 {@code <chain>} 标签内的 EL 表达式进行语法高亮：注释、关键字、组件、子流程、子变量、未知引用。
 * 解析基于自写的 {@link LiteFlowElLexer}，不再依赖 QLExpress。
 * </p>
 *
 * @author Bryan.Zhang
 */
public class LiteFlowChainAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlTag) || !LiteFlowXmlUtil.isElCarrierTag((XmlTag) element)) {
            return;
        }
        if (!(element.getContainingFile() instanceof XmlFile)
                || !LiteFlowXmlUtil.isLiteFlowXml((XmlFile) element.getContainingFile())) {
            return;
        }

        XmlTag elTag = (XmlTag) element;
        XmlTagValue value = elTag.getValue();
        if (value == null) {
            return;
        }
        String expressionText = value.getText();
        if (expressionText == null || expressionText.trim().isEmpty()) {
            return;
        }

        int valueOffset = value.getTextRange().getStartOffset();
        Project project = element.getProject();
        // 每轮 pass 构建一次解析器：优先当前文件实时 PSI，回退全局缓存
        LiteFlowElementResolver resolver = LiteFlowElementResolver.create(project, element.getContainingFile());

        List<LiteFlowElToken> tokens = LiteFlowElLexer.tokenize(expressionText);
        Map<String, LiteFlowElToken> subVarDefs = LiteFlowElLexer.findSubVarDefinitions(tokens);

        for (LiteFlowElToken token : tokens) {
            TextRange range = new TextRange(valueOffset + token.start, valueOffset + token.end);

            // 注释
            if (token.type == LiteFlowElToken.Type.COMMENT) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(range)
                        .textAttributes(LiteFlowHighlightColorSettings.EL_COMMENT_KEY)
                        .create();
                continue;
            }
            // 只对标识符分类高亮
            if (token.type != LiteFlowElToken.Type.IDENT) {
                continue;
            }

            String word = token.text;
            if (LiteFlowXmlUtil.isElKeyword(word)) {
                highlight(holder, range, LiteFlowHighlightColorSettings.EL_KEYWORD_KEY);
            } else if (subVarDefs.containsKey(word)) {
                highlight(holder, range, LiteFlowHighlightColorSettings.SUB_VARIABLE_KEY);
            } else if (resolver.isNode(word)) {
                highlight(holder, range, LiteFlowHighlightColorSettings.COMPONENT_KEY);
            } else if (resolver.isChain(word)) {
                highlight(holder, range, LiteFlowHighlightColorSettings.CHAIN_KEY);
            } else {
                highlight(holder, range, LiteFlowHighlightColorSettings.UNKNOWN_COMPONENT_KEY);
            }
        }
    }

    private static void highlight(@NotNull AnnotationHolder holder, @NotNull TextRange range,
                                  com.intellij.openapi.editor.colors.TextAttributesKey key) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(key)
                .create();
    }
}
