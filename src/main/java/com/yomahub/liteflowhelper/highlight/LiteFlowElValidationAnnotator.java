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
import com.yomahub.liteflowhelper.utils.ElValidationFinding;
import com.yomahub.liteflowhelper.utils.LiteFlowElAnalyzer;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * LiteFlow EL 表达式校验注解器：在 EL 承载标签（chain 直接值 / route / body）上
 * 运行 {@link LiteFlowElAnalyzer}，将结构与语义问题以 ERROR / WARNING 标注在编辑器中。
 * <p>类型不符/括号/IF 参数=ERROR；缺失 .TO/.DO=WARNING。</p>
 */
public class LiteFlowElValidationAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlTag) || !LiteFlowXmlUtil.isElCarrierTag((XmlTag) element)) {
            return;
        }
        if (!(element.getContainingFile() instanceof XmlFile)
                || !LiteFlowXmlUtil.isLiteFlowXml((XmlFile) element.getContainingFile())) {
            return;
        }

        XmlTag tag = (XmlTag) element;
        XmlTagValue value = tag.getValue();
        if (value == null) {
            return;
        }
        String expressionText = value.getText();
        if (expressionText == null || expressionText.trim().isEmpty()) {
            return;
        }

        int valueOffset = value.getTextRange().getStartOffset();
        Project project = element.getProject();
        LiteFlowElementResolver resolver = LiteFlowElementResolver.create(project, element.getContainingFile());

        List<ElValidationFinding> findings = LiteFlowElAnalyzer.analyze(expressionText, resolver::getNodeCategory);
        for (ElValidationFinding f : findings) {
            HighlightSeverity severity = f.severity == ElValidationFinding.Severity.ERROR
                    ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
            int absStart = valueOffset + f.start;
            int absEnd = valueOffset + f.end;
            if (absStart < 0 || absEnd <= absStart) {
                continue;
            }
            holder.newAnnotation(severity, f.message)
                    .range(new TextRange(absStart, absEnd))
                    .create();
        }
    }
}
