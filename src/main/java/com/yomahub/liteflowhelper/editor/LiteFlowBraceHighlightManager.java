package com.yomahub.liteflowhelper.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.yomahub.liteflowhelper.highlight.LiteFlowHighlightColorSettings;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责在 LiteFlow EL 表达式中高亮匹配的括号。
 * <p>
 * 此管理器被附加到特定的编辑器实例上，通过监听光标和文档事件来触发高亮逻辑。
 * 它实现了 {@link Disposable} 接口，以确保在编辑器关闭时能正确清理资源。
 * </p>
 */
public class LiteFlowBraceHighlightManager implements CaretListener, DocumentListener, Disposable {

    // 用于在 Editor 的 UserData 中存储本管理器的实例
    public static final Key<LiteFlowBraceHighlightManager> BRACE_HIGHLIGHTER_KEY = Key.create("LiteFlowBraceHighlightManager");

    private final Editor editor;
    private final List<RangeHighlighter> highlighters;

    /**
     * 将本管理器附加到一个新的编辑器实例上。
     * @param editor 要附加到的编辑器
     */
    public static void attachTo(@NotNull Editor editor) {
        Project project = editor.getProject();
        if (project == null || editor.getUserData(BRACE_HIGHLIGHTER_KEY) != null) {
            return;
        }
        // 每个编辑器实例都拥有自己的高亮管理器
        LiteFlowBraceHighlightManager manager = new LiteFlowBraceHighlightManager(editor);
        editor.putUserData(BRACE_HIGHLIGHTER_KEY, manager);
    }

    /**
     * 从编辑器实例中分离本管理器，并清理所有资源。
     * 这个方法应该在编辑器被释放时（例如在 EditorFactoryListener.editorReleased 中）调用。
     * @param editor 要分离的编辑器
     */
    public static void detachFrom(@NotNull Editor editor) {
        LiteFlowBraceHighlightManager manager = editor.getUserData(BRACE_HIGHLIGHTER_KEY);
        if (manager != null) {
            // 调用 Disposer.dispose 会触发 manager 的 dispose() 方法，
            // 从而安全地移除监听器和高亮。
            Disposer.dispose(manager);
            // 从编辑器的 user data 中移除引用，防止内存泄漏。
            editor.putUserData(BRACE_HIGHLIGHTER_KEY, null);
        }
    }


    private LiteFlowBraceHighlightManager(@NotNull Editor editor) {
        this.editor = editor;
        this.highlighters = new ArrayList<>();
        // 将 manager (this) 作为 Disposable parent 传入。
        // 这样，当本管理器实例的 dispose() 方法被调用时，这些监听器会被平台自动移除。
        editor.getCaretModel().addCaretListener(this, this);
        editor.getDocument().addDocumentListener(this, this);
        // 首次打开时，立即更新一次高亮状态
        updateBraceHighlighting();
    }

    /**
     * 销毁管理器，清理资源。
     * 这个方法由 Disposer.dispose(manager) 触发。
     */
    @Override
    public void dispose() {
        // 因为监听器在注册时已经关联了本 Disposable，所以它们会被自动移除。
        // 我们只需要在这里处理高亮清理的逻辑。
        // 这个清理操作也需要被调度到 EDT 中执行。
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!editor.isDisposed()) {
                removeHighlighters();
            }
        }, ModalityState.any());
    }

    @Override
    public void caretPositionChanged(@NotNull CaretEvent event) {
        updateBraceHighlighting();
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        // 文档变化也可能影响括号匹配（例如，通过粘贴代码）
        updateBraceHighlighting();
    }

    /**
     * 更新括号高亮的核心逻辑。
     * <p>
     * 将所有 PSI 读取和 MarkupModel 修改操作都调度到 Event Dispatch Thread (EDT) 中执行，
     * 以确保线程安全并解决 "Write-unsafe context" 异常。
     * </p>
     */
    private void updateBraceHighlighting() {
        // 将更新高亮的逻辑调度到事件分发线程 (EDT) 执行
        // 这是为了确保所有对编辑器模型（如MarkupModel）的修改都在一个“写安全”的上下文中进行，从而解决 "Write-unsafe context" 异常。
        ApplicationManager.getApplication().invokeLater(() -> {
            Project project = editor.getProject();
            // 必须在对编辑器进行任何操作之前检查其有效性
            if (project == null || project.isDisposed() || editor.isDisposed()) {
                return;
            }

            // 在执行任何操作之前先移除旧的高亮
            removeHighlighters();

            int caretOffset = editor.getCaretModel().getOffset();
            // 如果光标在文档开头，则无法获取前一个字符，直接返回
            if (caretOffset == 0) {
                return;
            }

            // 提交任何未保存的文档更改，以确保PSI树是最新的
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            if (psiFile == null) {
                return;
            }

            // 我们关心的是光标位置*之前*的字符，这通常是用户刚刚输入的或者光标所在位置的括号
            PsiElement element = psiFile.findElementAt(caretOffset - 1);
            if (element == null) {
                return;
            }

            // 查找包含该元素的XML标签
            XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
            // [修改] 目标标签应为 "chain"，并且要确保是LiteFlow的XML文件
            if (tag != null && "chain".equalsIgnoreCase(tag.getName()) && psiFile instanceof XmlFile && LiteFlowXmlUtil.isLiteFlowXml((XmlFile) psiFile)) {
                XmlTagValue value = tag.getValue();
                // 检查光标是否真的在标签的值文本范围内
                if (value == null || !value.getTextRange().contains(caretOffset - 1)) {
                    return;
                }

                String text = value.getText();
                int valueTextOffset = value.getTextRange().getStartOffset();
                int relativeCaretOffset = caretOffset - valueTextOffset;

                // 查找匹配的括号
                int matchingBraceOffset = findMatchingBrace(text, relativeCaretOffset);

                if (matchingBraceOffset != -1) {
                    // 高亮当前光标位置的括号 (实际上是光标前一个字符)
                    highlightBrace(caretOffset - 1);
                    // 高亮匹配的括号 (需要将相对偏移转换回文档的绝对偏移)
                    highlightBrace(valueTextOffset + matchingBraceOffset);
                }
            }
        }, ModalityState.defaultModalityState());
    }

    /**
     * 在给定的文本和偏移量处查找匹配的括号。
     * @param text 要搜索的文本 (then/when 标签内的EL表达式)
     * @param offset 光标相对于 'text' 的偏移量 (1-based)
     * @return 匹配括号相对于 'text' 的偏移量 (0-based)，如果未找到则返回 -1
     */
    private int findMatchingBrace(String text, int offset) {
        if (offset <= 0 || offset > text.length()) {
            return -1;
        }
        // 获取光标前的字符
        char charAtCaret = text.charAt(offset - 1);

        if (charAtCaret == '(') {
            int balance = 1;
            for (int i = offset; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '(') {
                    balance++;
                } else if (c == ')') {
                    balance--;
                    if (balance == 0) {
                        return i; // 找到匹配
                    }
                }
            }
        } else if (charAtCaret == ')') {
            int balance = 1;
            for (int i = offset - 2; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == ')') {
                    balance++;
                } else if (c == '(') {
                    balance--;
                    if (balance == 0) {
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
        if (editor.isDisposed()) {
            return;
        }
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
        if (highlighters.isEmpty() || editor.isDisposed()) {
            return;
        }
        MarkupModel markupModel = editor.getMarkupModel();
        for (RangeHighlighter highlighter : highlighters) {
            markupModel.removeHighlighter(highlighter);
        }
        highlighters.clear();
    }
}
