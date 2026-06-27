package com.yomahub.liteflowhelper.marker;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.yomahub.liteflowhelper.icon.LiteFlowIcons;
import com.yomahub.liteflowhelper.service.LiteFlowUsageFinder;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 在 Java 组件类左侧提供 gutter 图标，点击可导航到引用该组件的所有 chain（反向导航）。
 * <p>多目标时弹出自定义列表，每行显示 chain 的 id/name 及所在文件，定位到点击的图标旁。
 * 弹窗 model 使用纯数据 {@link ChainTarget}（不把 PsiElement 作为 model，渲染时不读 PSI），
 * 以符合平台约束并避免渲染期的 read-access 断言。</p>
 * <p>仅处理类级组件（继承式 / 类声明式）；方法级声明式（多 nodeId）不在此处理。</p>
 */
public class LiteFlowComponentUsageLineMarkerProvider implements LineMarkerProvider {

    /** 弹窗项：预算好的显示信息 + 导航所需的最小数据（无 PSI 依赖）。 */
    private static final class ChainTarget {
        final String id;
        final String fileName;
        final VirtualFile file;
        final int offset;

        ChainTarget(String id, String fileName, VirtualFile file, int offset) {
            this.id = id;
            this.fileName = fileName;
            this.file = file;
            this.offset = offset;
        }
    }

    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // 反向导航属于慢操作（需扫描全项目 chain），统一在 collectSlowLineMarkers 中处理
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                       @NotNull Collection<? super LineMarkerInfo<?>> result) {
        for (PsiElement element : elements) {
            if (!(element instanceof PsiIdentifier)) {
                continue;
            }
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiClass)) {
                continue;
            }
            PsiClass psiClass = (PsiClass) parent;
            // 仅在类名标识符上触发
            if (!element.equals(psiClass.getNameIdentifier())) {
                continue;
            }

            String nodeId = LiteFlowXmlUtil.getComponentNodeId(psiClass);
            if (nodeId == null) {
                continue;
            }

            List<XmlTag> chains = LiteFlowUsageFinder.findChainsReferencingNode(psiClass.getProject(), nodeId);
            if (chains.isEmpty()) {
                continue;
            }

            result.add(new LineMarkerInfo<>(
                    element,
                    element.getTextRange(),
                    LiteFlowIcons.CHAIN_ICON,
                    el -> "被 " + chains.size() + " 个 chain 引用（点击跳转）",
                    (e, el) -> navigateToUsages(e, psiClass.getProject(), nodeId, chains),
                    GutterIconRenderer.Alignment.RIGHT,
                    () -> "LiteFlow 组件反向导航"
            ));
        }
    }

    /** 单个引用直接跳转；多个引用弹出"chain id (文件)"列表，定位到点击的图标旁。 */
    private static void navigateToUsages(@Nullable MouseEvent e, @NotNull Project project,
                                         @NotNull String nodeId, @NotNull List<XmlTag> chains) {
        // 在 read action 中预算每个 chain 的显示信息与导航坐标，避免渲染期读 PSI
        List<ChainTarget> targets = ApplicationManager.getApplication().runReadAction(
                (Computable<List<ChainTarget>>) () -> {
                    List<ChainTarget> r = new ArrayList<>();
                    for (XmlTag tag : chains) {
                        if (!tag.isValid()) {
                            continue;
                        }
                        VirtualFile vf = tag.getContainingFile().getVirtualFile();
                        if (vf == null) {
                            continue;
                        }
                        r.add(new ChainTarget(chainIdOf(tag), tag.getContainingFile().getName(), vf, tag.getTextOffset()));
                    }
                    return r;
                });

        if (targets.isEmpty()) {
            return;
        }
        if (targets.size() == 1) {
            open(project, targets.get(0));
            return;
        }

        JBPopup popup = JBPopupFactory.getInstance()
                .createPopupChooserBuilder(targets)
                .setTitle("引用 '" + nodeId + "' 的 chain")
                .setRenderer(new ColoredListCellRenderer<ChainTarget>() {
                    @Override
                    protected void customizeCellRenderer(@NotNull JList<? extends ChainTarget> list, ChainTarget t,
                                                         int index, boolean selected, boolean hasFocus) {
                        setIcon(LiteFlowIcons.CHAIN_ICON);
                        append(t.id, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                        append("   (" + t.fileName + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    }
                })
                .setItemChosenCallback(t -> open(project, t))
                .createPopup();

        // 定位到鼠标点击的图标位置
        if (e != null) {
            popup.show(new RelativePoint(e));
        } else {
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor != null) {
                popup.showInBestPositionFor(editor);
            } else {
                popup.showCenteredInCurrentWindow(project);
            }
        }
    }

    private static void open(@NotNull Project project, @NotNull ChainTarget t) {
        new OpenFileDescriptor(project, t.file, t.offset).navigate(true);
    }

    /** chain 的显示名：优先 name 属性，其次 id 属性，再回退标签名。 */
    private static String chainIdOf(@NotNull XmlTag tag) {
        String id = tag.getAttributeValue("name");
        if (id == null || id.isEmpty()) {
            id = tag.getAttributeValue("id");
        }
        if (id == null || id.isEmpty()) {
            id = tag.getName();
        }
        return id;
    }
}
