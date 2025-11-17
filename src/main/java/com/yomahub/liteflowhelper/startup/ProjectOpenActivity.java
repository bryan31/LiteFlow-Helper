package com.yomahub.liteflowhelper.startup;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.Alarm;
import com.yomahub.liteflowhelper.listener.LiteFlowFileChangeListener;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowChainScanner;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowNodeScanner;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 在项目启动后执行的活动。
 * <p>
 * 这个类的主要作用是在后台预加载和缓存 LiteFlow 的 Chain 和 Node 数据。
 * 不会自动打开工具窗口，但会在后台完成数据扫描和缓存，
 * 这样用户打开工具窗口时可以立即看到数据，同时解决 XML 规则报红的问题。
 * </p>
 */
public class ProjectOpenActivity implements StartupActivity.DumbAware {
    private static final Logger LOG = Logger.getInstance(ProjectOpenActivity.class);

    // [优化] 提取魔法数字为常量
    private static final int MAX_RETRY_COUNT = 2;
    private static final long BASE_RETRY_DELAY_MS = 3000L;

    @Override
    public void runActivity(@NotNull Project project) {
        // 注册文件变化监听器
        project.getMessageBus().connect().subscribe(
                com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
                new LiteFlowFileChangeListener(project)
        );
        LOG.info("LiteFlow 文件变化监听器已注册");

        // 等待项目索引完成后再执行后台扫描
        DumbService.getInstance(project).runWhenSmart(() -> {
            LOG.info("项目启动完成，开始后台预加载 LiteFlow 数据");
            preloadLiteFlowData(project, 0);
        });
    }

    /**
     * 在后台预加载 LiteFlow 的 Chain 和 Node 数据到缓存中
     *
     * @param project     当前项目
     * @param retryCount  当前重试次数
     */
    private void preloadLiteFlowData(@NotNull Project project, int retryCount) {
        if (project.isDisposed()) {
            return;
        }

        LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(project);

        // 只有在缓存有数据且不是首次加载时才跳过
        // 这样可以确保重试机制正常工作
        if (!cacheService.isCacheEmpty() && retryCount == 0) {
            LOG.info("LiteFlow 缓存已有数据，跳过预加载");
            return;
        }

        // 在后台任务中执行扫描
        Task.Backgroundable task = new Task.Backgroundable(project, "预加载 LiteFlow 数据", false) {
            private List<ChainInfo> foundChains;
            private List<LiteFlowNodeInfo> foundNodes;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                LOG.info("后台任务：开始扫描 LiteFlow chains 和 nodes（第 " + (retryCount + 1) + " 次尝试）");
                indicator.setIndeterminate(false);
                indicator.setFraction(0.0);

                LiteFlowChainScanner chainScanner = new LiteFlowChainScanner();
                LiteFlowNodeScanner nodeScanner = new LiteFlowNodeScanner();

                indicator.setText("正在扫描 LiteFlow chains...");
                foundChains = chainScanner.findChains(project);
                indicator.setFraction(0.5);

                indicator.checkCanceled();

                indicator.setText("正在扫描 LiteFlow nodes...");
                foundNodes = nodeScanner.findLiteFlowNodes(project);
                indicator.setFraction(1.0);

                LOG.info("后台任务：扫描完成，找到 " + foundChains.size() + " 个 chains 和 " + foundNodes.size() + " 个 nodes");
            }

            @Override
            public void onSuccess() {
                if (project.isDisposed()) {
                    return;
                }

                // 如果扫描结果为空且重试次数未达上限，则延迟后重试
                if (foundChains.isEmpty() && foundNodes.isEmpty() && retryCount < MAX_RETRY_COUNT) {
                    int nextRetryCount = retryCount + 1;
                    long delayMs = BASE_RETRY_DELAY_MS * nextRetryCount; // 第1次重试延迟3秒，第2次重试延迟6秒
                    LOG.warn("扫描结果为空（第 " + (retryCount + 1) + " 次尝试），将在 " + delayMs + "ms 后重试");

                    // 使用 Alarm 来调度延迟任务
                    Alarm alarm = new Alarm();
                    alarm.addRequest(() -> {
                        if (!project.isDisposed()) {
                            DumbService.getInstance(project).runWhenSmart(() -> {
                                LOG.info("开始第 " + (nextRetryCount + 1) + " 次扫描重试");
                                preloadLiteFlowData(project, nextRetryCount);
                            });
                        }
                        alarm.dispose();
                    }, (int) delayMs);
                } else if (!foundChains.isEmpty() || !foundNodes.isEmpty()) {
                    // 只有找到组件时才更新缓存
                    cacheService.updateCache(foundChains, foundNodes);
                    LOG.info("LiteFlow 数据已成功预加载到缓存: " + foundChains.size() + " 个chains, " + foundNodes.size() + " 个nodes");
                } else {
                    // 达到最大重试次数仍未找到组件
                    LOG.info("经过 " + (retryCount + 1) + " 次尝试，未找到任何 LiteFlow 组件。");
                    LOG.info("可能的原因：");
                    LOG.info("1. 项目中没有 LiteFlow 组件");
                    LOG.info("2. LiteFlow 依赖未正确添加到项目");
                    LOG.info("3. 项目索引尚未完成，请等待索引完成后手动刷新");
                    LOG.info("4. 组件类不在项目源码目录中");
                    // 即使没找到也更新缓存为空，避免无限重试
                    cacheService.updateCache(foundChains, foundNodes);
                }
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.error("预加载 LiteFlow 数据时发生错误", error);

                // 发生错误时，如果还有重试机会，也尝试重试
                if (retryCount < MAX_RETRY_COUNT && !project.isDisposed()) {
                    int nextRetryCount = retryCount + 1;
                    LOG.info("将在 " + BASE_RETRY_DELAY_MS + " 毫秒后重试（第 " + (nextRetryCount + 1) + " 次尝试）");

                    Alarm alarm = new Alarm();
                    alarm.addRequest(() -> {
                        if (!project.isDisposed()) {
                            DumbService.getInstance(project).runWhenSmart(() -> {
                                preloadLiteFlowData(project, nextRetryCount);
                            });
                        }
                        alarm.dispose();
                    }, (int) BASE_RETRY_DELAY_MS);
                }
            }
        };

        ProgressManager.getInstance().run(task);
    }
}
