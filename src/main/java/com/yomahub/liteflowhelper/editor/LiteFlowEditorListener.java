package com.yomahub.liteflowhelper.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 编辑器工厂监听器。
 * <p>
 * 当一个编辑器被创建时，这个监听器会检查它打开的是否是LiteFlow的XML规则文件。
 * 如果是，它会为这个编辑器附加一个 {@link LiteFlowBraceHighlightManager} 实例来处理括号高亮。
 * </p>
 */
public class LiteFlowEditorListener implements EditorFactoryListener {

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }

        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (virtualFile == null) {
            return;
        }

        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        // 判断是否为 LiteFlow XML 文件
        if (psiFile instanceof XmlFile && LiteFlowXmlUtil.isLiteFlowXml((XmlFile) psiFile)) {
            // 为这个编辑器附加括号高亮管理器
            LiteFlowBraceHighlightManager.attachTo(editor);
        }
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        // 当编辑器被关闭或释放时，从编辑器中移除我们的高亮管理器，以防止内存泄漏
        LiteFlowBraceHighlightManager.detachFrom(editor);
    }
}
