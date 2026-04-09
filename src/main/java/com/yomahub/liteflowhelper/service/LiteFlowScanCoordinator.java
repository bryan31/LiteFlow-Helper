package com.yomahub.liteflowhelper.service;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowChainScanner;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowNodeScanner;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowXmlScanResult;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowXmlScanner;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 统一协调 LiteFlow 全量扫描，避免多个入口重复触发。
 */
@Service(Service.Level.PROJECT)
public final class LiteFlowScanCoordinator {
    private static final Logger LOG = Logger.getInstance(LiteFlowScanCoordinator.class);

    private final Project project;
    private final LiteFlowCacheService cacheService;
    private final LiteFlowXmlScanner xmlScanner = new LiteFlowXmlScanner();
    private final LiteFlowChainScanner chainScanner = new LiteFlowChainScanner();
    private final LiteFlowNodeScanner nodeScanner = new LiteFlowNodeScanner();
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);
    private final AtomicReference<ScanRequest> queuedRequest = new AtomicReference<>();

    public LiteFlowScanCoordinator(@NotNull Project project) {
        this.project = project;
        this.cacheService = LiteFlowCacheService.getInstance(project);
    }

    public static LiteFlowScanCoordinator getInstance(@NotNull Project project) {
        return project.getService(LiteFlowScanCoordinator.class);
    }

    public boolean isScanInProgress() {
        return scanInProgress.get();
    }

    public void requestScan(@NotNull LiteFlowScanTrigger trigger, boolean forceRescan, boolean queueIfRunning) {
        requestScan(trigger, forceRescan, queueIfRunning, -1L);
    }

    public void requestScan(
            @NotNull LiteFlowScanTrigger trigger,
            boolean forceRescan,
            boolean queueIfRunning,
            long refreshVersion
    ) {
        if (project.isDisposed()) {
            return;
        }

        Runnable task = () -> startOrQueueScan(new ScanRequest(trigger, forceRescan, queueIfRunning, refreshVersion));
        if (DumbService.getInstance(project).isDumb()) {
            LOG.debug("项目处于 dumb mode，延后执行 LiteFlow 扫描: " + trigger);
            DumbService.getInstance(project).runWhenSmart(task);
            return;
        }

        task.run();
    }

    private void startOrQueueScan(@NotNull ScanRequest request) {
        if (!scanInProgress.compareAndSet(false, true)) {
            if (request.queueIfRunning) {
                queuedRequest.accumulateAndGet(request, ScanRequest::merge);
                LOG.info("LiteFlow 扫描已在进行中，合并后续请求: " + request.trigger);
            } else {
                LOG.debug("LiteFlow 扫描已在进行中，忽略重复请求: " + request.trigger);
            }
            return;
        }

        project.getMessageBus().syncPublisher(LiteFlowScanListener.TOPIC).scanStarted(request.trigger);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "扫描 LiteFlow 数据", true) {
            private List<ChainInfo> foundChains = Collections.emptyList();
            private List<LiteFlowNodeInfo> foundNodes = Collections.emptyList();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                indicator.setFraction(0.0);
                indicator.setText("正在扫描 LiteFlow XML...");

                LiteFlowXmlScanResult xmlScanResult = xmlScanner.scan(project);
                foundChains = chainScanner.findChains(project, xmlScanResult);
                indicator.setFraction(0.5);
                indicator.checkCanceled();

                indicator.setText("正在扫描 LiteFlow 节点...");
                foundNodes = nodeScanner.findLiteFlowNodes(project, xmlScanResult);
                indicator.setFraction(1.0);

                LOG.info("LiteFlow 扫描完成，触发源: " + request.trigger
                        + "，chains=" + foundChains.size()
                        + "，nodes=" + foundNodes.size());
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }

                cacheService.updateCache(foundChains, foundNodes);
                LiteFlowRefreshStateService.getInstance(project).markRefreshCompleted(request.refreshVersion);
                DaemonCodeAnalyzer.getInstance(project).restart();
                project.getMessageBus().syncPublisher(LiteFlowScanListener.TOPIC).scanFinished(request.trigger);
            }

            @Override
            public void onCancel() {
                LOG.info("LiteFlow 扫描被取消，触发源: " + request.trigger);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.error("LiteFlow 扫描失败，触发源: " + request.trigger, error);
            }

            @Override
            public void onFinished() {
                scanInProgress.set(false);
                LiteFlowRefreshStateService.getInstance(project).updateNotifications();
                ScanRequest nextRequest = queuedRequest.getAndSet(null);
                if (nextRequest != null && !project.isDisposed()) {
                    requestScan(
                            nextRequest.trigger,
                            nextRequest.forceRescan,
                            nextRequest.queueIfRunning,
                            nextRequest.refreshVersion
                    );
                }
            }
        });
    }

    private static final class ScanRequest {
        private final LiteFlowScanTrigger trigger;
        private final boolean forceRescan;
        private final boolean queueIfRunning;
        private final long refreshVersion;

        private ScanRequest(
                @NotNull LiteFlowScanTrigger trigger,
                boolean forceRescan,
                boolean queueIfRunning,
                long refreshVersion
        ) {
            this.trigger = trigger;
            this.forceRescan = forceRescan;
            this.queueIfRunning = queueIfRunning;
            this.refreshVersion = refreshVersion;
        }

        private static ScanRequest merge(@NotNull ScanRequest current, @NotNull ScanRequest incoming) {
            return new ScanRequest(
                    incoming.trigger,
                    current.forceRescan || incoming.forceRescan,
                    current.queueIfRunning || incoming.queueIfRunning,
                    Math.max(current.refreshVersion, incoming.refreshVersion)
            );
        }
    }
}
