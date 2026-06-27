package com.yomahub.liteflowhelper.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
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
 * 检查 LiteFlow EL 表达式中引用的组件 / 子流程是否存在。
 * <p>
 * 解析基于自写的 {@link LiteFlowElLexer}，不再依赖 QLExpress。
 * </p>
 */
public class LiteFlowMissingComponentInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof XmlTag)) {
                    return;
                }
                XmlTag tag = (XmlTag) element;
                if (!LiteFlowXmlUtil.isElCarrierTag(tag)) {
                    return;
                }
                if (!(tag.getContainingFile() instanceof XmlFile)
                        || !LiteFlowXmlUtil.isLiteFlowXml((XmlFile) tag.getContainingFile())) {
                    return;
                }

                XmlTagValue value = tag.getValue();
                String expressionText = value.getText();
                if (expressionText == null || expressionText.trim().isEmpty()) {
                    return;
                }

                int valueOffset = value.getTextRange().getStartOffset();
                int tagOffset = tag.getTextRange().getStartOffset();
                Project project = holder.getProject();
                // 优先当前文件实时 PSI，回退全局缓存
                LiteFlowElementResolver resolver = LiteFlowElementResolver.create(project, tag.getContainingFile());

                List<LiteFlowElToken> tokens = LiteFlowElLexer.tokenize(expressionText);
                Map<String, LiteFlowElToken> subVarDefs = LiteFlowElLexer.findSubVarDefinitions(tokens);

                for (LiteFlowElToken token : tokens) {
                    if (token.type != LiteFlowElToken.Type.IDENT) {
                        continue;
                    }
                    String name = token.text;
                    // 关键字、子变量（定义或引用）跳过
                    if (LiteFlowXmlUtil.isElKeyword(name) || subVarDefs.containsKey(name)) {
                        continue;
                    }
                    // 已知的组件 / 子流程跳过（含当前文件实时定义）
                    if (resolver.isNode(name) || resolver.isChain(name)) {
                        continue;
                    }

                    int absStart = valueOffset + token.start;
                    int absEnd = valueOffset + token.end;
                    TextRange rangeInTag = new TextRange(absStart - tagOffset, absEnd - tagOffset);
                    holder.registerProblem(tag, rangeInTag, "LiteFlow component '" + name + "' not found");
                }
            }
        };
    }
}
