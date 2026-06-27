package com.yomahub.liteflowhelper.listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.yomahub.liteflowhelper.service.LiteFlowCacheRefresher;
import com.yomahub.liteflowhelper.utils.LiteFlowRelevance;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * VFS 文件变更监听器：当 LiteFlow 相关的文件落盘变化时，触发带防抖的缓存刷新。
 * <p>
 * 实际的扫描、防抖由 {@link LiteFlowCacheRefresher} 统一承担（与 PSI 监听器共享），
 * 本类只做轻量的相关性过滤。
 * </p>
 */
public class LiteFlowFileChangeListener implements BulkFileListener {

    private final Project project;

    public LiteFlowFileChangeListener(Project project) {
        this.project = project;
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        if (project.isDisposed()) {
            return;
        }
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (LiteFlowRelevance.isRelevant(file)) {
                LiteFlowCacheRefresher.getInstance(project).requestRefresh();
                return;
            }
        }
    }
}
