package com.yomahub.liteflowhelper.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.yomahub.liteflowhelper.service.LiteFlowUsageFinder;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * 检查 LiteFlow 组件是否被任何 chain 引用：未被引用的组件给出 WARNING。
 * <p>仅检查类级组件（继承式 / 类声明式）。被引用判断基于全项目所有 chain 的 EL。</p>
 */
public class LiteFlowUnusedComponentInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            /** 当前文件检查内懒加载一次"全项目被引用 nodeId 集合"，避免逐组件重复扫描。 */
            private Set<String> referenced;

            private @NotNull Set<String> referenced() {
                if (referenced == null) {
                    referenced = LiteFlowUsageFinder.collectReferencedNodeIds(holder.getProject());
                }
                return referenced;
            }

            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof PsiClass)) {
                    return;
                }
                PsiClass psiClass = (PsiClass) element;
                String nodeId = LiteFlowXmlUtil.getComponentNodeId(psiClass);
                if (nodeId == null) {
                    return;
                }
                if (!referenced().contains(nodeId)) {
                    PsiElement nameIdentifier = psiClass.getNameIdentifier();
                    PsiElement where = nameIdentifier != null ? nameIdentifier : psiClass;
                    holder.registerProblem(where,
                            "LiteFlow 组件 '" + nodeId + "' 未被任何 chain 引用",
                            ProblemHighlightType.WARNING);
                }
            }
        };
    }
}
