package com.yomahub.liteflowhelper.format;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.yomahub.liteflowhelper.utils.ElFormatOptions;
import com.yomahub.liteflowhelper.utils.ElTextEscaper;
import com.yomahub.liteflowhelper.utils.FormatResult;
import com.yomahub.liteflowhelper.utils.LiteFlowElFormatter;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * EL 格式化的 IDE 集成共用逻辑：定位光标处 EL 承载标签、计算缩进基准、执行写命令。
 * 供 {@link LiteFlowFormatElIntention}（Alt+Enter）与 {@link LiteFlowFormatElAction}（右键菜单）共用。
 */
public final class ElFormatSupport {

    /** 行宽预算与缩进单位（常量，暂不做设置页）。 */
    private static final int MAX_LINE_WIDTH = 80;
    private static final int INDENT_WIDTH = 4;

    private ElFormatSupport() {
    }

    /**
     * 定位 offset 处的 EL 承载标签（chain 直接值 / route / body）。
     * 须在在读锁内调用。光标不在 value 文本内、非 LiteFlow XML 时返回 null。
     */
    public static @Nullable XmlTag findCarrierTag(@NotNull PsiFile file, int offset) {
        if (!(file instanceof XmlFile) || !LiteFlowXmlUtil.isLiteFlowXml((XmlFile) file)) {
            return null;
        }
        PsiElement element = file.findElementAt(offset);
        XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
        if (tag == null && offset > 0) {
            // 光标在边界（如 </chain> 之前）时回退一个字符再试
            element = file.findElementAt(offset - 1);
            tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
        }
        while (tag != null) {
            if (LiteFlowXmlUtil.isElCarrierTag(tag)) {
                XmlTagValue value = tag.getValue();
                if (value != null
                        && value.getTextRange().getStartOffset() <= offset
                        && offset <= value.getTextRange().getEndOffset()) {
                    return tag;
                }
                return null;
            }
            PsiElement parent = tag.getParent();
            tag = (parent instanceof XmlTag) ? (XmlTag) parent : null;
        }
        return null;
    }

    /** 标签 value 文本是否含非空白内容（空 EL 不提供格式化入口）。 */
    public static boolean hasNonBlankEl(@NotNull XmlTag tag) {
        XmlTagValue value = tag.getValue();
        return value != null && !value.getText().trim().isEmpty();
    }

    /** 对 offset 处的 EL 承载标签执行格式化；失败时用错误气泡提示。 */
    public static void formatEl(@NotNull Project project, @NotNull Editor editor,
                                @NotNull PsiFile file, int offset) {
        XmlTag tag = findCarrierTag(file, offset);
        if (tag == null) {
            return;
        }
        XmlTagValue value = tag.getValue();
        if (value == null) {
            return;
        }
        String fullText = value.getText();
        // 只重写"EL 语句区"：第一个非空白字符到最后一个非空白字符之间，首尾空白原样保留
        int lead = 0;
        while (lead < fullText.length() && Character.isWhitespace(fullText.charAt(lead))) {
            lead++;
        }
        int trail = fullText.length();
        while (trail > lead && Character.isWhitespace(fullText.charAt(trail - 1))) {
            trail--;
        }
        if (lead >= trail) {
            return;
        }
        String elText = fullText.substring(lead, trail);

        Document document = editor.getDocument();
        int valueStart = value.getTextRange().getStartOffset();
        int elStart = valueStart + lead;
        int elLine = document.getLineNumber(elStart);
        int firstLineColumn = elStart - document.getLineStartOffset(elLine);

        boolean ownLine = fullText.substring(0, lead).contains("\n");
        final int baseIndent;
        if (ownLine) {
            // EL 已另起一行：续行缩进沿用其现有缩进
            baseIndent = firstLineColumn;
        } else {
            // EL 与 <chain ...> 同行：续行缩进 = 标签列 + 缩进单位
            int tagLine = document.getLineNumber(tag.getTextOffset());
            baseIndent = (tag.getTextOffset() - document.getLineStartOffset(tagLine)) + INDENT_WIDTH;
        }

        FormatResult result = LiteFlowElFormatter.format(elText,
                new ElFormatOptions(MAX_LINE_WIDTH, INDENT_WIDTH, baseIndent, firstLineColumn));
        if (!result.success) {
            HintManager.getInstance().showErrorHint(editor, result.reason + "，无法格式化");
            return;
        }
        if (result.formatted.equals(elText)) {
            return; // 无变化，不写文档
        }
        String escaped = ElTextEscaper.escape(result.formatted);
        // lambda 只能捕获最终变量，这里拷贝替换区间
        final int replaceStart = valueStart + lead;
        final int replaceEnd = valueStart + trail;
        WriteCommandAction.runWriteCommandAction(project, "格式化 LiteFlow EL", null,
                () -> document.replaceString(replaceStart, replaceEnd, escaped));
    }
}
