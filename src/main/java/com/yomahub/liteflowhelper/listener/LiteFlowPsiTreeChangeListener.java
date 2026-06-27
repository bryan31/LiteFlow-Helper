package com.yomahub.liteflowhelper.listener;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.yomahub.liteflowhelper.service.LiteFlowCacheRefresher;
import com.yomahub.liteflowhelper.utils.LiteFlowRelevance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PSI 树变更监听器：当 LiteFlow 相关的 XML / Java 文件发生 PSI 变更（含已提交但未落盘的改动）时，
 * 触发带防抖的缓存刷新，使补全 / 工具窗口等依赖缓存的消费方更及时地反映变化。
 * <p>
 * 实际的扫描与防抖由 {@link LiteFlowCacheRefresher} 承担，本类只做轻量的相关性过滤。
 * </p>
 */
public class LiteFlowPsiTreeChangeListener extends PsiTreeChangeAdapter {

    private final Project project;

    public LiteFlowPsiTreeChangeListener(Project project) {
        this.project = project;
    }

    private void maybeRefresh(@Nullable PsiElement element) {
        if (element == null) {
            return;
        }
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }
        VirtualFile vf = file.getVirtualFile();
        if (LiteFlowRelevance.isRelevant(vf)) {
            LiteFlowCacheRefresher.getInstance(project).requestRefresh();
        }
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
        maybeRefresh(event.getParent());
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        maybeRefresh(event.getParent());
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        maybeRefresh(event.getParent());
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        maybeRefresh(event.getParent());
    }
}
