package com.yomahub.liteflowhelper.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * 在项目启动后执行的活动。
 * <p>
 * 这个类的主要作用是自动打开 "LiteFlow Chain Window" 工具窗口。
 * 这样做可以触发数据扫描和缓存填充，解决在未打开工具窗口时XML规则报红的问题。
 * </p>
 */
public class ProjectOpenActivity implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        // 获取项目级的 ToolWindowManager
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);

        // 使用 invokeLater 确保窗口操作在事件分发线程（EDT）上执行，这是Swing的强制要求
        toolWindowManager.invokeLater(() -> {
            // 通过 plugin.xml 中定义的 ID 查找我们的工具窗口
            ToolWindow toolWindow = toolWindowManager.getToolWindow("LiteFlow Chain Window");

            // 如果找到了工具窗口并且它当前不是可见状态，则激活它
            if (toolWindow != null && !toolWindow.isVisible()) {
                // activate 方法会显示并可选地聚焦工具窗口
                // 这将触发 ChainWindowFactory 的 createToolWindowContent 方法
                toolWindow.activate(null, true);
            }
        });
    }
}
