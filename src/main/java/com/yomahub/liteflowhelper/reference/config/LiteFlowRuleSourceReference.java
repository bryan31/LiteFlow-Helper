package com.yomahub.liteflowhelper.reference.config;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 为 liteflow.ruleSource 的值提供文件引用。
 * <p>
 * 这个类实现了 PsiPolyVariantReference 接口，使其能够处理以下两种情况：
 * 1. 单一文件引用：当路径是精确路径时，直接跳转到该文件。
 * 2. 多文件引用：当路径包含通配符 (如 "flow*.xml") 时，查找所有匹配的文件，并提供一个列表供用户选择。
 * </p>
 *
 * @author Bryan.Zhang
 */
public class LiteFlowRuleSourceReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

    private final String resourcePath;

    public LiteFlowRuleSourceReference(@NotNull PsiElement element, TextRange textRange, String resourcePath) {
        super(element, textRange);
        // 统一路径分隔符为'/'，以便与VirtualFile系统匹配
        this.resourcePath = resourcePath.replace('\\', '/');
    }

    /**
     * 解析引用，可能返回多个结果。
     * 这是处理通配符路径的核心方法。
     *
     * @param incompleteCode 代码是否不完整，此处我们不关心。
     * @return 返回所有匹配的解析结果。
     */
    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        Project project = myElement.getProject();
        PsiManager psiManager = PsiManager.getInstance(project);
        List<ResolveResult> results = new ArrayList<>();

        // [修正] 使用 PatternUtil.fromMask 将 Ant 风格的通配符 (*, ?) 转换为 Java 正则表达式。
        // 这是替代旧的 StringMatcher 的现代方法。
        final Pattern pattern = PatternUtil.fromMask(this.resourcePath);

        // [修正] 使用 ProjectRootManager.getInstance(project).getContentSourceRoots() 来获取项目所有模块的源文件和资源根目录。
        // 这是替代遍历模块并调用 getSourceRoots(false) 的更稳定和简洁的方法。
        final VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();

        // 遍历每个资源根目录，查找匹配的文件
        for (VirtualFile root : sourceRoots) {
            // 递归地遍历资源根目录下的所有文件
            VfsUtilCore.iterateChildrenRecursively(root, null, fileOrDir -> {
                if (!fileOrDir.isDirectory()) {
                    // 获取文件相对于资源根目录的相对路径
                    String relativePath = VfsUtilCore.getRelativePath(fileOrDir, root, '/');
                    // 使用正则表达式的 matcher 检查路径是否匹配
                    if (relativePath != null && pattern.matcher(relativePath).matches()) {
                        PsiFile psiFile = psiManager.findFile(fileOrDir);
                        if (psiFile != null) {
                            // 如果匹配，将其作为 PsiElementResolveResult 添加到结果列表中
                            results.add(new PsiElementResolveResult(psiFile));
                        }
                    }
                }
                return true; // 继续遍历
            });
        }

        return results.toArray(ResolveResult.EMPTY_ARRAY);
    }

    /**
     * 解析引用，返回单个结果。
     * 如果 multiResolve 返回了唯一的结果，则此方法会返回该结果，实现直接跳转。
     *
     * @return 如果只有一个匹配项，则返回对应的 PsiElement，否则返回 null。
     */
    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }

    /**
     * 这个方法可以用来提供代码补全的变体，我们暂时不实现。
     *
     * @return 返回一个空数组。
     */
    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;
    }
}
