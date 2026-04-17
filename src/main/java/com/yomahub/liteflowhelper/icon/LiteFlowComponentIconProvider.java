package com.yomahub.liteflowhelper.icon;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * 为 LiteFlow 的 Java 组件类提供自定义图标。
 * <p>
 * 这个类会检查项目中的 Java 类，如果它们符合特定条件（继承式、类声明式、方法声明式），
 * 就在项目树视图中将其默认图标替换为指定的自定义图标。
 * </p>
 */
public class LiteFlowComponentIconProvider extends IconProvider {

    // 加载普通组件图标
    private static final Icon COMMON_COMPONENT_ICON = IconLoader.getIcon("/icons/common.svg", LiteFlowComponentIconProvider.class);
    // 加载方法声明式组件图标
    private static final Icon MULTI_COMPONENT_ICON = IconLoader.getIcon("/icons/multi.svg", LiteFlowComponentIconProvider.class);

    /**
     * IntelliJ Platform 调用此方法来获取元素的图标。
     *
     * @param element PSI 元素，我们只关心 Java 类 (PsiClass)。
     * @param flags   一些附加的标志，当前未使用。
     * @return 如果 element 是一个 LiteFlow 组件类，则返回自定义图标；否则返回 null。
     */
    @Nullable
    @Override
    public Icon getIcon(@NotNull PsiElement element, int flags) {
        Project project = element.getProject();
        // 确保项目已完成索引，避免在 "Dumb Mode" 下执行操作。
        if (project == null || DumbService.getInstance(project).isDumb()) {
            return null;
        }

        // 我们只处理 Java 类
        if (element instanceof PsiClass) {
            PsiClass psiClass = (PsiClass) element;

            // 1. 判断是否为继承式组件
            if (LiteFlowXmlUtil.isInheritanceComponent(psiClass)) {
                return COMMON_COMPONENT_ICON;
            }

            // 2. 判断是否为类声明式组件
            if (LiteFlowXmlUtil.isClassDeclarativeComponent(psiClass)) {
                return COMMON_COMPONENT_ICON;
            }

            // 3. 判断类中是否包含方法声明式组件
            // 使用重载后的 isMethodDeclarativeComponent(PsiClass) 方法，使代码更简洁
            if (LiteFlowXmlUtil.isMethodDeclarativeComponent(psiClass)) {
                return MULTI_COMPONENT_ICON;
            }
        }

        // 对于所有其他情况，返回 null，IDEA 将使用默认图标。
        return null;
    }
}
