package com.yomahub.liteflowhelper;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowChainScanner;
import com.yomahub.liteflowhelper.toolwindow.service.LiteFlowNodeScanner;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 项目启动活动，用于在项目打开并索引完成后预热LiteFlow组件缓存。
 * 这确保了Annotator和ReferenceContributor等功能在用户打开文件时可以立即访问到数据，
 * 而无需等待工具窗口被首次打开。
 *
 * @author Bryan.Zhang
 */
public class PluginStartupActivity implements StartupActivity.DumbAware {

    private static final Logger LOG = Logger.getInstance(PluginStartupActivity.class);

    @Override
    public void runActivity(@NotNull Project project) {
        // 在后台任务中运行初始扫描，以避免阻塞UI线程
        // DumbService.runWhenSmart() 确保这个任务在项目索引完成后执行
        DumbService.getInstance(project).runWhenSmart(() -> runInitialScan(project));
    }

    private void runInitialScan(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }

        Task.Backgroundable task = new Task.Backgroundable(project, "正在扫描LiteFlow组件", false) {
            private List<ChainInfo> foundChains;
            private List<LiteFlowNodeInfo> foundNodes;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                LOG.info("项目启动：开始扫描LiteFlow chains和nodes。");
                indicator.setIndeterminate(true);
                indicator.setText("正在扫描 LiteFlow 定义...");

                // 使用scanner来查找
                LiteFlowChainScanner chainScanner = new LiteFlowChainScanner();
                LiteFlowNodeScanner nodeScanner = new LiteFlowNodeScanner();
                foundChains = chainScanner.findChains(getProject());
                foundNodes = nodeScanner.findLiteFlowNodes(getProject());

                LOG.info("项目启动：扫描完成。");
            }

            @Override
            public void onSuccess() {
                if (getProject() == null || getProject().isDisposed()) {
                    return;
                }
                LOG.info("项目启动：扫描成功，正在更新缓存。");

                // 获取缓存服务并更新
                LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(getProject());
                cacheService.updateCache(foundChains, foundNodes);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.error("在项目启动时扫描LiteFlow组件时发生错误", error);
            }
        };

        ProgressManager.getInstance().run(task);
    }
}
