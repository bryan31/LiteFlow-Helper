package com.yomahub.liteflowhelper.format;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.yomahub.liteflowhelper.utils.LiteFlowElFormatter;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LiteFlowPostFormatProcessor implements PostFormatProcessor {

    @Override
    public @NotNull PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
        PsiFile file = source.getContainingFile();
        if (file != null) {
            processText(file, source.getTextRange(), settings);
        }
        return source;
    }

    @Override
    public @NotNull TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat,
                                          @NotNull CodeStyleSettings settings) {
        if (!(source instanceof XmlFile xmlFile) || !LiteFlowXmlUtil.isLiteFlowXml(xmlFile)) {
            return rangeToReformat;
        }

        Project project = source.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(source);
        if (document == null) {
            return rangeToReformat;
        }

        List<Replacement> replacements = collectReplacements(xmlFile, document, rangeToReformat, settings);
        if (replacements.isEmpty()) {
            return rangeToReformat;
        }

        for (int i = replacements.size() - 1; i >= 0; i--) {
            Replacement replacement = replacements.get(i);
            document.replaceString(replacement.range.getStartOffset(), replacement.range.getEndOffset(), replacement.text);
        }

        PsiDocumentManager.getInstance(project).commitDocument(document);
        return rangeToReformat;
    }

    @Override
    public boolean isWhitespaceOnly() {
        return false;
    }

    private List<Replacement> collectReplacements(XmlFile xmlFile, Document document, TextRange rangeToReformat,
                                                  CodeStyleSettings settings) {
        List<Replacement> replacements = new ArrayList<>();
        String indentUnit = resolveIndentUnit(xmlFile, settings);

        for (XmlTag tag : PsiTreeUtil.findChildrenOfType(xmlFile, XmlTag.class)) {
            if (!"chain".equals(tag.getName())) {
                continue;
            }

            XmlTagValue value = tag.getValue();
            if (value == null) {
                continue;
            }

            TextRange valueRange = value.getTextRange();
            if (!valueRange.intersects(rangeToReformat)) {
                continue;
            }

            String currentText = value.getText();
            if (currentText.trim().isEmpty()) {
                continue;
            }

            String tagIndent = getLineIndent(document, tag.getTextRange().getStartOffset());
            String formattedText = LiteFlowElFormatter.formatChainBody(currentText, tagIndent, indentUnit);
            if (!formattedText.equals(currentText)) {
                replacements.add(new Replacement(valueRange, formattedText));
            }
        }

        return replacements;
    }

    private String resolveIndentUnit(PsiFile file, CodeStyleSettings settings) {
        var indentOptions = settings.getIndentOptions(file.getFileType());
        if (indentOptions == null) {
            return "    ";
        }
        if (indentOptions.USE_TAB_CHARACTER) {
            return "\t";
        }
        return " ".repeat(Math.max(1, indentOptions.INDENT_SIZE));
    }

    private String getLineIndent(Document document, int offset) {
        int lineNumber = document.getLineNumber(offset);
        int lineStartOffset = document.getLineStartOffset(lineNumber);
        CharSequence chars = document.getCharsSequence();
        int indentEnd = lineStartOffset;

        while (indentEnd < offset) {
            char c = chars.charAt(indentEnd);
            if (c != ' ' && c != '\t') {
                break;
            }
            indentEnd++;
        }

        return chars.subSequence(lineStartOffset, indentEnd).toString();
    }

    private record Replacement(TextRange range, String text) {
    }
}
