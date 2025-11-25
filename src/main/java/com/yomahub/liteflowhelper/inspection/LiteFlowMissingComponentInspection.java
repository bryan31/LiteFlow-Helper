package com.yomahub.liteflowhelper.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.ql.util.express.ExpressRunner;
import com.yomahub.liteflowhelper.service.LiteFlowCacheService;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspection to check if components used in LiteFlow EL expressions exist.
 */
public class LiteFlowMissingComponentInspection extends LocalInspectionTool {

    private static final ExpressRunner EXPRESS_RUNNER = new ExpressRunner();
    private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*\\*.*?\\*\\*/", Pattern.DOTALL);
    private static final Pattern SUB_VAR_PATTERN = Pattern.compile("\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*=");

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof XmlTag)) {
                    return;
                }
                XmlTag tag = (XmlTag) element;
                if (!"chain".equals(tag.getName())) {
                    return;
                }
                if (!(tag.getContainingFile() instanceof XmlFile)
                        || !LiteFlowXmlUtil.isLiteFlowXml((XmlFile) tag.getContainingFile())) {
                    return;
                }

                XmlTagValue value = tag.getValue();
                String expressionText = value.getText();
                if (expressionText == null || expressionText.trim().isEmpty()) {
                    return;
                }

                int valueOffset = value.getTextRange().getStartOffset();
                Project project = holder.getProject();
                LiteFlowCacheService cacheService = LiteFlowCacheService.getInstance(project);

                // Mask comments
                StringBuilder maskedExpressionBuilder = new StringBuilder(expressionText);
                Matcher commentMatcher = COMMENT_PATTERN.matcher(expressionText);
                while (commentMatcher.find()) {
                    for (int i = commentMatcher.start(); i < commentMatcher.end(); i++) {
                        maskedExpressionBuilder.setCharAt(i, ' ');
                    }
                }
                String maskedExpressionText = maskedExpressionBuilder.toString();

                // Find local variable definitions
                Set<String> localVars = new HashSet<>();
                Matcher subVarMatcher = SUB_VAR_PATTERN.matcher(maskedExpressionText);
                while (subVarMatcher.find()) {
                    localVars.add(subVarMatcher.group(1));
                }

                try {
                    String[] outVarNames = EXPRESS_RUNNER.getOutVarNames(maskedExpressionText);
                    for (String varName : outVarNames) {
                        if (LiteFlowXmlUtil.isElKeyword(varName)) {
                            continue;
                        }

                        // Check if it's a local variable
                        if (localVars.contains(varName)) {
                            continue;
                        }

                        // Check if it's a known component or chain
                        if (!cacheService.containsCachedNode(varName) && !cacheService.containsCachedChain(varName)) {
                            // Find location
                            Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(varName) + "\\b");
                            Matcher matcher = varPattern.matcher(maskedExpressionText);
                            while (matcher.find()) {
                                // Double check if it is really an assignment (to be safe against regex false
                                // positives)
                                boolean isAssignment = false;
                                int nextCharIdx = matcher.end();
                                while (nextCharIdx < maskedExpressionText.length()
                                        && Character.isWhitespace(maskedExpressionText.charAt(nextCharIdx))) {
                                    nextCharIdx++;
                                }
                                if (nextCharIdx < maskedExpressionText.length()
                                        && maskedExpressionText.charAt(nextCharIdx) == '=') {
                                    isAssignment = true;
                                }

                                if (!isAssignment) {
                                    // Calculate range relative to the tag
                                    int tagOffset = tag.getTextRange().getStartOffset();
                                    int absoluteStart = valueOffset + matcher.start();
                                    int absoluteEnd = valueOffset + matcher.end();
                                    TextRange rangeInTag = new TextRange(absoluteStart - tagOffset,
                                            absoluteEnd - tagOffset);

                                    holder.registerProblem(tag, rangeInTag,
                                            "LiteFlow component '" + varName + "' not found");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore parse errors
                }
            }
        };
    }
}
