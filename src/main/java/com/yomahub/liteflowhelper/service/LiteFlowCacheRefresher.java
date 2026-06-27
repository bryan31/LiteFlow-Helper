package com.yomahub.liteflowhelper.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowChainScanner;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowNodeScanner;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 统一的 LiteFlow 缓存刷新调度器（项目级服务）。
 * <p>
 * 提供带防抖的后台全量扫描，可被 VFS 监听器、PSI 监听器等多个来源安全地反复调用。
 * 相比原先仅依赖 VFS（落盘）触发，PSI 维度的触发能让"已提交到 PSI 但尚未落盘"的改动
 * 更快地反映到缓存（从而影响补全、工具窗口等仍依赖缓存的消费方）。
 * </p>
 */
@Service(Service.Level.PROJECT)
public final class LiteFlowCacheRefresher {

    private static final Logger LOG = Logger.getInstance(LiteFlowCacheRefresher.class);

    /** 防抖间隔：最短间隔内只允许触发一次刷新 */
    private static final long DEBOUNCE_MS = 1500L;

    private final Project project;
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final AtomicLong lastRefresh = new AtomicLong(0);

    public LiteFlowCacheRefresher(@NotNull Project project) {
        this.project = project;
    }

    public static LiteFlowCacheRefresher getInstance(@NotNull Project project) {
        return project.getService(LiteFlowCacheRefresher.class);
    }

    /**
     * 请求一次带防抖的后台刷新。线程安全，可被多个监听器反复调用。
     */
    public void requestRefresh() {
        if (project.isDisposed() || DumbService.getInstance(project).isDumb()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRefresh.get() < DEBOUNCE_MS) {
            return;
        }
        // CAS 确保同一时间只有一个刷新任务
        if (scheduled.compareAndSet(false, true)) {
            lastRefresh.set(now);
            LOG.debug("请求刷新 LiteFlow 组件缓存");
            DumbService.getInstance(project).runWhenSmart(() ->
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (project.isDisposed()) {
                            scheduled.set(false);
                            return;
                        }
                        performRefresh();
                    }));
        }
    }

    private void performRefresh() {
        Task.Backgroundable task = new Task.Backgroundable(project, "更新 LiteFlow 组件缓存", false) {
            private List<ChainInfo> foundChains;
            private List<LiteFlowNodeInfo> foundNodes;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                indicator.setFraction(0.0);
                indicator.setText("正在扫描 Chains...");
                foundChains = new LiteFlowChainScanner().findChains(project);
                indicator.setFraction(0.5);
                indicator.checkCanceled();
                indicator.setText("正在扫描 Nodes...");
                foundNodes = new LiteFlowNodeScanner().findLiteFlowNodes(project);
                indicator.setFraction(1.0);
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }
                LiteFlowCacheService.getInstance(project).updateCache(foundChains, foundNodes);
                LOG.debug("LiteFlow 组件缓存已更新");
            }

            @Override
            public void onFinished() {
                // 无论成功、取消还是异常都会调用，确保调度标志复位
                scheduled.set(false);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.error("刷新 LiteFlow 组件缓存时发生错误", error);
            }
        };
        ProgressManager.getInstance().run(task);
    }
}
