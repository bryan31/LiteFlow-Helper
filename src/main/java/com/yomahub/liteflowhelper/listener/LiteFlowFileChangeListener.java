package com.yomahub.liteflowhelper.listener;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowChainScanner;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowNodeScanner;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 监听文件变化，当检测到 LiteFlow 相关的 Java 文件或 XML 文件变化时，
 * 自动触发组件重新扫描，确保缓存数据始终保持最新。
 * [优化] 提取魔法数字为常量，增强可配置性
 */
public class LiteFlowFileChangeListener implements BulkFileListener {
    private static final Logger LOG = Logger.getInstance(LiteFlowFileChangeListener.class);

    private final Project project;
    private final AtomicBoolean refreshScheduled = new AtomicBoolean(false);
    private final AtomicLong lastRefreshTime = new AtomicLong(0);

    // [优化] 防抖间隔：最短3秒才能再次触发刷新
    private static final long DEBOUNCE_INTERVAL_MS = 3000L;

    public LiteFlowFileChangeListener(Project project) {
        this.project = project;
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        if (project.isDisposed() || DumbService.getInstance(project).isDumb()) {
            return;
        }

        // 检查是否有相关文件变化
        boolean hasRelevantChange = false;
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (file != null && isRelevantFile(file)) {
                hasRelevantChange = true;
                break;
            }
        }

        if (!hasRelevantChange) {
            return;
        }

        // 防抖：避免短时间内多次刷新
        long currentTime = System.currentTimeMillis();
        long lastTime = lastRefreshTime.get();
        if (currentTime - lastTime < DEBOUNCE_INTERVAL_MS) {
            LOG.debug("文件变化检测到，但距离上次刷新时间太短，跳过本次刷新");
            return;
        }

        // 使用 CAS 确保同一时间只有一个刷新任务
        if (refreshScheduled.compareAndSet(false, true)) {
            lastRefreshTime.set(currentTime);
            LOG.info("检测到 LiteFlow 相关文件变化，准备刷新组件缓存");
            scheduleRefresh();
        }
    }

    /**
     * 判断文件是否与 LiteFlow 相关（Java 组件文件或 XML 配置文件）
     * [性能优化] 添加更精准的文件过滤，减少不必要的缓存刷新
     */
    private boolean isRelevantFile(VirtualFile file) {
        if (file.isDirectory()) {
            return false;
        }

        String extension = file.getExtension();

        // 检查是否是 Java 文件（可能是组件类）
        if ("java".equalsIgnoreCase(extension)) {
            // [优化] 排除明显不相关的目录
            String path = file.getPath();
            // 排除测试目录、生成代码目录等
            if (path.contains("/test/") ||
                path.contains("/generated/") ||
                path.contains("/build/") ||
                path.contains("/target/")) {
                return false;
            }
            return true;
        }

        // 检查是否是 XML 文件（可能是 LiteFlow 规则配置）
        if ("xml".equalsIgnoreCase(extension)) {
            // [优化] 只关注可能的 LiteFlow 配置文件
            String name = file.getName().toLowerCase();
            // 包含 flow、liteflow、chain 或 rule 关键字的 XML 文件更可能是 LiteFlow 配置
            return name.contains("flow") ||
                   name.contains("liteflow") ||
                   name.contains("chain") ||
                   name.contains("rule");
        }

        return false;
    }

    /**
     * 调度一个后台任务来刷新组件缓存
     */
    private void scheduleRefresh() {
        // 等待索引完成后再执行
        DumbService.getInstance(project).runWhenSmart(() -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) {
                    refreshScheduled.set(false);
                    return;
                }

                performRefresh();
            });
        });
    }

    /**
     * 执行实际的刷新操作
     */
    private void performRefresh() {
        Task.Backgroundable task = new Task.Backgroundable(project, "更新 LiteFlow 组件缓存", false) {
            private List<ChainInfo> foundChains;
            private List<LiteFlowNodeInfo> foundNodes;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    LOG.info("开始扫描 LiteFlow 组件...");
                    indicator.setIndeterminate(false);
                    indicator.setFraction(0.0);

                    LiteFlowChainScanner chainScanner = new LiteFlowChainScanner();
                    LiteFlowNodeScanner nodeScanner = new LiteFlowNodeScanner();

                    indicator.setText("正在扫描 Chains...");
                    foundChains = chainScanner.findChains(project);
                    indicator.setFraction(0.5);

                    indicator.checkCanceled();

                    indicator.setText("正在扫描 Nodes...");
                    foundNodes = nodeScanner.findLiteFlowNodes(project);
                    indicator.setFraction(1.0);

                    LOG.info("扫描完成：找到 " + foundChains.size() + " 个 chains 和 "
                            + foundNodes.size() + " 个 nodes");
                } catch (Exception e) {
                    LOG.error("扫描 LiteFlow 组件时发生错误", e);
                }
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }

                // 更新缓存
                LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(project);
                cacheService.updateCache(foundChains, foundNodes);
                LOG.info("LiteFlow 组件缓存已更新");
            }

            @Override
            public void onFinished() {
                refreshScheduled.set(false);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.error("刷新 LiteFlow 组件缓存时发生错误", error);
                refreshScheduled.set(false);
            }
        };

        ProgressManager.getInstance().run(task);
    }
}
