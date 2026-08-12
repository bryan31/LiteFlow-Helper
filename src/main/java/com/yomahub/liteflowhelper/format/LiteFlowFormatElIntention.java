package com.yomahub.liteflowhelper.format;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * Alt+Enter 意图：格式化光标所在 chain 的 LiteFlow EL 表达式。
 */
public class LiteFlowFormatElIntention implements IntentionAction {

    @Override
    public @NotNull String getText() {
        return "格式化 LiteFlow EL 表达式";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "LiteFlow";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (editor == null || file == null) {
            return false;
        }
        XmlTag tag = ElFormatSupport.findCarrierTag(file, editor.getCaretModel().getOffset());
        return tag != null && ElFormatSupport.hasNonBlankEl(tag);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        ElFormatSupport.formatEl(project, editor, file, editor.getCaretModel().getOffset());
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
