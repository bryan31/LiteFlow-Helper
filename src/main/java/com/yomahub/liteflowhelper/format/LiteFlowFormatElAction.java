package com.yomahub.liteflowhelper.format;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * 编辑器右键菜单 Action：格式化光标所在 chain 的 LiteFlow EL 表达式。
 * 仅在 LiteFlow XML 且光标位于 EL 承载标签内时可见可用；不绑定默认快捷键。
 */
public class LiteFlowFormatElAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        Caret caret = e.getData(CommonDataKeys.CARET);
        boolean available = e.getProject() != null && editor != null && file != null && caret != null
                && ReadAction.compute(() -> {
                    XmlTag tag = ElFormatSupport.findCarrierTag(file, caret.getOffset());
                    return tag != null && ElFormatSupport.hasNonBlankEl(tag);
                });
        e.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        Caret caret = e.getData(CommonDataKeys.CARET);
        if (project == null || editor == null || file == null || caret == null) {
            return;
        }
        ElFormatSupport.formatEl(project, editor, file, caret.getOffset());
    }
}
