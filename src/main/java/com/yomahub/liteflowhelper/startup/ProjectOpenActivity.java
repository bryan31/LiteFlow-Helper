package com.yomahub.liteflowhelper.startup;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.yomahub.liteflowhelper.listener.LiteFlowDocumentChangeListener;
import com.yomahub.liteflowhelper.listener.LiteFlowFileChangeListener;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.service.LiteFlowScanCoordinator;
import com.yomahub.liteflowhelper.service.LiteFlowScanTrigger;
import org.jetbrains.annotations.NotNull;

/**
 * 项目启动后触发一次后台预热扫描。
 */
public class ProjectOpenActivity implements StartupActivity.DumbAware {
    private static final Logger LOG = Logger.getInstance(ProjectOpenActivity.class);

    @Override
    public void runActivity(@NotNull Project project) {
        project.getMessageBus().connect().subscribe(
                com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
                new LiteFlowFileChangeListener(project)
        );
        LOG.info("LiteFlow 文件变化监听器已注册");

        EditorFactory.getInstance()
                .getEventMulticaster()
                .addDocumentListener(new LiteFlowDocumentChangeListener(project), project);
        LOG.info("LiteFlow 文档变化监听器已注册");

        DumbService.getInstance(project).runWhenSmart(() -> {
            LOG.info("项目启动完成，准备预热 LiteFlow 缓存");
            LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(project);
            if (cacheService.isCacheEmpty()) {
                LiteFlowScanCoordinator.getInstance(project)
                        .requestScan(LiteFlowScanTrigger.STARTUP, false, false);
            }
        });
    }
}
