package com.yomahub.liteflowhelper.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 记录 LiteFlow 相关文件的待同步状态，并负责刷新编辑器横幅。
 */
@Service(Service.Level.PROJECT)
public final class LiteFlowRefreshStateService {
    private final Project project;
    private final AtomicLong changeVersion = new AtomicLong();
    private final Map<String, Long> pendingFiles = new ConcurrentHashMap<>();

    public LiteFlowRefreshStateService(@NotNull Project project) {
        this.project = project;
    }

    public static LiteFlowRefreshStateService getInstance(@NotNull Project project) {
        return project.getService(LiteFlowRefreshStateService.class);
    }

    public void markFileChanged(@NotNull VirtualFile file) {
        if (project.isDisposed()) {
            return;
        }

        long version = changeVersion.incrementAndGet();
        pendingFiles.put(file.getUrl(), version);
        updateNotifications();
    }

    public boolean shouldShowNotification(@NotNull VirtualFile file) {
        return pendingFiles.containsKey(file.getUrl());
    }

    public void requestManualRefresh() {
        if (project.isDisposed()) {
            return;
        }

        long refreshVersion = changeVersion.get();
        updateNotifications();
        LiteFlowScanCoordinator.getInstance(project)
                .requestScan(LiteFlowScanTrigger.MANUAL_REFRESH, true, true, refreshVersion);
    }

    public void markRefreshCompleted(long refreshVersion) {
        if (project.isDisposed() || refreshVersion < 0) {
            return;
        }

        pendingFiles.entrySet().removeIf(entry -> entry.getValue() <= refreshVersion);
        updateNotifications();
    }

    public void updateNotifications() {
        if (!project.isDisposed()) {
            EditorNotifications.getInstance(project).updateAllNotifications();
        }
    }
}
