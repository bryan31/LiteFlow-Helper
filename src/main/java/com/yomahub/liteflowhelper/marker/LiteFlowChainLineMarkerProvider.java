package com.yomahub.liteflowhelper.marker;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.yomahub.liteflowhelper.icon.LiteFlowIcons;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 为Java代码中调用LiteFlow chain的地方提供Gutter图标导航.
 *
 * @author Gemini
 */
public class LiteFlowChainLineMarkerProvider implements LineMarkerProvider {

    private static final String FLOW_EXECUTOR_CLASS = "com.yomahub.liteflow.core.FlowExecutor";

    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // 目标是方法名，它是一个 PsiIdentifier
        if (!(element instanceof PsiIdentifier)) {
            return null;
        }

        // PsiIdentifier 的父元素应该是 PsiReferenceExpression，祖父元素是 PsiMethodCallExpression
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiReferenceExpression)) {
            return null;
        }

        PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression callExpr)) {
            return null;
        }

        // 解析方法调用，确认它是否是 FlowExecutor 的目标方法
        PsiMethod resolvedMethod = callExpr.resolveMethod();
        if (resolvedMethod == null) {
            return null;
        }

        // 检查方法名
        String methodName = resolvedMethod.getName();
        if (!methodName.startsWith("execute") || "executeRouteChain".equals(methodName)) {
            return null;
        }

        // 检查方法所属的类
        PsiClass containingClass = resolvedMethod.getContainingClass();
        if (containingClass == null || !FLOW_EXECUTOR_CLASS.equals(containingClass.getQualifiedName())) {
            return null;
        }

        // 获取参数列表，检查第一个参数是否是字符串字面量
        PsiExpression[] args = callExpr.getArgumentList().getExpressions();
        if (args.length == 0) {
            return null;
        }

        String chainId;
        // 只处理字符串字面量的情况
        if (args[0] instanceof PsiLiteralExpression literalExpression) {
            Object value = literalExpression.getValue();
            if (value instanceof String) {
                chainId = (String) value;
            } else {
                chainId = null;
            }
        } else {
            chainId = null;
        }

        if (chainId == null || chainId.isEmpty()) {
            return null;
        }

        // 从缓存中查找 chainId 是否存在
        Project project = element.getProject();
        LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(project);
        Optional<ChainInfo> chainInfoOpt = cacheService.getCachedChains().stream()
                .filter(c -> chainId.equals(c.getName()))
                .findFirst();

        // 如果 chain 不存在，则不显示图标
        if (chainInfoOpt.isEmpty()) {
            return null;
        }

        // --- 已修正的逻辑 ---
        // 获取 chain 定义所在的 PsiFile 和 offset
        ChainInfo chainInfo = chainInfoOpt.get();
        PsiFile chainFile = chainInfo.getPsiFile();
        int offset = chainInfo.getOffset();

        if (chainFile == null || !chainFile.isValid()) {
            return null;
        }

        // 通过 offset 在文件中找到对应的 PsiElement
        PsiElement elementAtOffset = chainFile.findElementAt(offset);
        if (elementAtOffset == null) {
            return null;
        }

        // 从找到的 element 向上追溯，找到它所属的 XmlTag
        XmlTag tag = PsiTreeUtil.getParentOfType(elementAtOffset, XmlTag.class, false);
        if (tag == null || !tag.isValid()) {
            return null;
        }
        // --- 修正结束 ---

        // 创建一个可导航的 Gutter 图标
        NavigationGutterIconBuilder<PsiElement> builder =
                NavigationGutterIconBuilder.create(LiteFlowIcons.CHAIN_ICON)
                        .setTarget(tag) // 设置导航目标为 chain 定义的 XmlTag
                        .setTooltipText("导航到["+chainId+"]的定义");

        // 将 LineMarkerInfo 附加到方法名(PsiIdentifier)上
        return builder.createLineMarkerInfo(element);
    }
}
