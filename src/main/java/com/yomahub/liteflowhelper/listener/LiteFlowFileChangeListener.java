package com.yomahub.liteflowhelper.listener;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.yomahub.liteflowhelper.service.LiteFlowRefreshStateService;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 监听 LiteFlow 相关文件变化，只提示手动同步，不自动触发扫描。
 */
public final class LiteFlowFileChangeListener implements BulkFileListener {
    private final Project project;

    public LiteFlowFileChangeListener(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        if (project.isDisposed()) {
            return;
        }

        Set<VirtualFile> changedFiles = new LinkedHashSet<>();
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (file != null && isRelevantFile(file)) {
                changedFiles.add(file);
            }
        }

        if (changedFiles.isEmpty()) {
            return;
        }

        LiteFlowRefreshStateService refreshStateService = LiteFlowRefreshStateService.getInstance(project);
        changedFiles.forEach(refreshStateService::markFileChanged);
    }

    private boolean isRelevantFile(@NotNull VirtualFile file) {
        if (file.isDirectory() || !file.isValid()) {
            return false;
        }

        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        if (!fileIndex.isInContent(file)) {
            return false;
        }

        String extension = file.getExtension();
        if (extension == null) {
            return false;
        }

        if ("java".equalsIgnoreCase(extension)) {
            return fileIndex.isInSourceContent(file);
        }

        if (!"xml".equalsIgnoreCase(extension)) {
            return false;
        }

        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        if (lowerName.contains("flow")
                || lowerName.contains("liteflow")
                || lowerName.contains("chain")
                || lowerName.contains("rule")) {
            return true;
        }

        if (DumbService.getInstance(project).isDumb()) {
            return false;
        }

        return ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            return psiFile instanceof XmlFile xmlFile && LiteFlowXmlUtil.isLiteFlowXml(xmlFile);
        });
    }
}
