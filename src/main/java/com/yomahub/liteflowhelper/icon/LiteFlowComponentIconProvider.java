package com.yomahub.liteflowhelper.icon;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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

            // 为了调用判断逻辑，需要获取 NodeComponent 的 PsiClass 实例
            PsiClass nodeComponentBaseClass = JavaPsiFacade.getInstance(project).findClass(
                    LiteFlowXmlUtil.NODE_COMPONENT_CLASS, GlobalSearchScope.allScope(project)
            );

            // 1. 判断是否为继承式组件
            if (LiteFlowXmlUtil.isInheritanceComponent(psiClass, nodeComponentBaseClass)) {
                return COMMON_COMPONENT_ICON;
            }

            // 2. 判断是否为类声明式组件
            if (LiteFlowXmlUtil.isClassDeclarativeComponent(psiClass, nodeComponentBaseClass)) {
                return COMMON_COMPONENT_ICON;
            }

            // 3. 判断类中是否包含方法声明式组件
            // 即使类本身不构成一个 "类声明式组件"，它也可能是一个包含 "方法声明式组件" 的容器。
            for (PsiMethod method : psiClass.getMethods()) {
                if (LiteFlowXmlUtil.isMethodDeclarativeComponent(method)) {
                    // 只要类中有一个方法是方法声明式组件，就认为这个类应该显示 multi.svg 图标
                    return MULTI_COMPONENT_ICON;
                }
            }
        }

        // 对于所有其他情况，返回 null，IDEA 将使用默认图标。
        return null;
    }
}
