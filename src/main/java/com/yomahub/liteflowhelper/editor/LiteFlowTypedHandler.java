package com.yomahub.liteflowhelper.editor;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.yomahub.liteflowhelper.utils.LiteFlowXmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 处理 LiteFlow EL 表达式中括号的自动补全。
 * 当在 <chain> 标签内输入 '(' 时，自动插入 ')'。
 */
public class LiteFlowTypedHandler extends TypedHandlerDelegate {
    @NotNull
    @Override
    public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        // 只处理 '(' 的输入
        if (c == '(') {
            // 确保文件是 XML 文件
            if (!(file instanceof XmlFile)) {
                return Result.CONTINUE;
            }

            // 获取当前光标位置，并找到光标前的 PSI 元素
            int offset = editor.getCaretModel().getOffset();
            // findElementAt(offset - 1) 是关键，它检查的是刚刚输入的那个字符所在的位置
            PsiElement element = file.findElementAt(offset - 1);
            if (element == null) {
                return Result.CONTINUE;
            }

            // 检查当前元素是否在 LiteFlow XML 的 <chain> 标签内
            XmlTag chainTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
            if (chainTag != null && "chain".equals(chainTag.getName()) && LiteFlowXmlUtil.isLiteFlowXml((XmlFile) file)) {
                // 如果条件满足，在光标当前位置插入配对的右括号
                editor.getDocument().insertString(offset, ")");
                // 返回 STOP，表示我们已经处理了这次输入，IDE 无需再执行默认操作
                return Result.STOP;
            }
        }
        // 对于其他所有字符，继续执行默认的输入处理
        return Result.CONTINUE;
    }
}
