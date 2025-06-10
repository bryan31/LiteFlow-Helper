package com.yomahub.liteflowhelper.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.yomahub.liteflowhelper.highlight.LiteFlowHighlightColorSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * 负责在 LiteFlow EL 表达式中高亮匹配的括号。
 * <p>
 * 此管理器被附加到特定的编辑器实例上，通过监听光标和文档事件来触发高亮逻辑。
 * 它实现了 {@link Disposable} 接口，以确保在编辑器关闭时能正确清理资源。
 * </p>
 */
public class LiteFlowBraceHighlightManager implements CaretListener, DocumentListener, Disposable {

    private final Editor editor;
    private final List<RangeHighlighter> highlighters = new ArrayList<>();

    private static final Key<LiteFlowBraceHighlightManager> MANAGER_KEY = Key.create("LiteFlowBraceHighlightManager");

    private LiteFlowBraceHighlightManager(@NotNull Editor editor) {
        this.editor = editor;
        // 添加监听器
        editor.getCaretModel().addCaretListener(this);
        editor.getDocument().addDocumentListener(this);
    }

    /**
     * 将管理器附加到给定的编辑器上。
     *
     * @param editor 目标编辑器
     */
    public static void attachTo(@NotNull Editor editor) {
        if (editor.getProject() == null) return;
        // 确保每个编辑器只有一个此管理器的实例
        if (editor.getUserData(MANAGER_KEY) == null) {
            LiteFlowBraceHighlightManager manager = new LiteFlowBraceHighlightManager(editor);
            editor.putUserData(MANAGER_KEY, manager);
            // 首次附加时，立即执行一次高亮检查
            manager.updateBraces();
        }
    }

    /**
     * 从给定的编辑器上分离并清理管理器。
     *
     * @param editor 目标编辑器
     */
    public static void detachFrom(@NotNull Editor editor) {
        LiteFlowBraceHighlightManager manager = editor.getUserData(MANAGER_KEY);
        if (manager != null) {
            manager.dispose();
            editor.putUserData(MANAGER_KEY, null);
        }
    }

    @Override
    public void dispose() {
        // 移除监听器
        editor.getCaretModel().removeCaretListener(this);
        editor.getDocument().removeDocumentListener(this);
        // 清除所有高亮
        removeHighlighters();
    }

    @Override
    public void caretPositionChanged(@NotNull CaretEvent event) {
        updateBraces();
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        // 文档变化时也需要更新，因为文本的偏移量可能都变了
        updateBraces();
    }

    /**
     * [重构后的核心逻辑] 核心更新逻辑：查找并高亮括号。
     */
    private void updateBraces() {
        // 1. 清除上一次的高亮
        removeHighlighters();

        // 2. 获取必要的上下文信息
        final int offset = editor.getCaretModel().getOffset();
        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }

        // [重要修改]
        // 在访问 PSI 之前，确保文档的更改已提交到 PSI 树。
        // 这可以防止在快速输入时，因 PSI 树更新延迟而导致的高亮计算错误。
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        psiDocumentManager.commitDocument(editor.getDocument());

        PsiFile psiFile = psiDocumentManager.getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return;
        }

        // 3. 检查光标是否在 <chain> 标签的内容区域内
        final PsiElement elementAtCaret = psiFile.findElementAt(offset > 0 ? offset - 1 : 0);
        if (elementAtCaret == null) {
            return;
        }
        XmlTag chainTag = PsiTreeUtil.getParentOfType(elementAtCaret, XmlTag.class, false);

        // 确保找到了名为 "chain" 的标签
        if (chainTag == null || !"chain".equals(chainTag.getName())) {
            return;
        }

        XmlTagValue tagValue = chainTag.getValue();
        // 确保 chainTag 包含有效的值区域，并且光标确实在其内部
        if (tagValue == null || tagValue.getTextRange().isEmpty() || !tagValue.getTextRange().contains(offset > 0 ? offset - 1 : 0)) {
            return;
        }

        // 4. 在 <chain> 标签的 *完整* 值内部执行括号匹配
        int textStartOffset = tagValue.getTextRange().getStartOffset();
        String valueText = tagValue.getText();

        int caretInValue = offset - textStartOffset;
        int lBrace = -1, rBrace = -1;

        // 核心规则：只在光标的左边是括号时才触发匹配
        if (caretInValue > 0 && caretInValue <= valueText.length()) {
            char charBefore = valueText.charAt(caretInValue - 1);
            if (charBefore == '(') {
                lBrace = caretInValue - 1;
                rBrace = findMatchingBraceInValue(valueText, lBrace, true);
            } else if (charBefore == ')') {
                rBrace = caretInValue - 1;
                lBrace = findMatchingBraceInValue(valueText, rBrace, false);
            }
        }

        // 5. 如果找到了匹配对，则应用高亮
        if (lBrace != -1 && rBrace != -1) {
            highlightBrace(textStartOffset + lBrace);
            highlightBrace(textStartOffset + rBrace);
        }
    }

    /**
     * 在给定的字符串中查找匹配的括号。
     * @param value       要搜索的字符串 (chain的值)
     * @param startIndex  起始括号在字符串中的索引
     * @param findClosing true表示从'('向前找')'，false表示从')'向后找'('
     * @return 匹配括号的索引，如果未找到则返回 -1
     */
    private int findMatchingBraceInValue(String value, int startIndex, boolean findClosing) {
        Stack<Integer> stack = new Stack<>();
        if (findClosing) { // 向前查找 ')'
            for (int i = startIndex; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '(') {
                    stack.push(i);
                } else if (c == ')') {
                    if (stack.isEmpty()) return -1; // 不匹配
                    stack.pop();
                    if (stack.isEmpty()) {
                        return i; // 找到匹配
                    }
                }
            }
        } else { // 向后查找 '('
            for (int i = startIndex; i >= 0; i--) {
                char c = value.charAt(i);
                if (c == ')') {
                    stack.push(i);
                } else if (c == '(') {
                    if (stack.isEmpty()) return -1; // 不匹配
                    stack.pop();
                    if (stack.isEmpty()) {
                        return i; // 找到匹配
                    }
                }
            }
        }
        return -1; // 未找到
    }

    /**
     * 在指定偏移量处添加一个括号高亮。
     * @param offset 要高亮的字符的文档偏移量
     */
    private void highlightBrace(int offset) {
        MarkupModel markupModel = editor.getMarkupModel();
        // 从您预定义的颜色设置中获取高亮属性
        TextAttributes attributes = LiteFlowHighlightColorSettings.MATCHED_BRACE_KEY.getDefaultAttributes();
        // 定义高亮层级，确保它在其他语法高亮之上，但在选区之下
        final int HIGHLIGHTER_LAYER = HighlighterLayer.SELECTION - 1;
        RangeHighlighter highlighter = markupModel.addRangeHighlighter(
                offset,
                offset + 1,
                HIGHLIGHTER_LAYER,
                attributes,
                HighlighterTargetArea.EXACT_RANGE
        );
        highlighters.add(highlighter);
    }

    /**
     * 移除所有由本管理器创建的高亮。
     */
    private void removeHighlighters() {
        if(highlighters.isEmpty()){
            return;
        }
        MarkupModel markupModel = editor.getMarkupModel();
        for (RangeHighlighter highlighter : highlighters) {
            markupModel.removeHighlighter(highlighter);
        }
        highlighters.clear();
    }
}
