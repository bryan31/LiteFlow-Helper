package com.yomahub.liteflowhelper.highlight;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * LiteFlow 插件高亮颜色定义
 *
 * @author Bryan.Zhang
 */
public class LiteFlowHighlightColorSettings {

    // 无法识别的组件高亮（红色粗体）
    public static final TextAttributesKey UNKNOWN_COMPONENT_KEY = createTextAttributesKey(
            "LITEFLOW_UNKNOWN_COMPONENT",
            new TextAttributes(new Color(231, 127, 127), null, null, null, Font.BOLD)
    );

    // 可识别的组件高亮（绿色粗体）
    public static final TextAttributesKey KNOWN_COMPONENT_KEY = createTextAttributesKey(
            "LITEFLOW_KNOWN_COMPONENT",
            new TextAttributes(new Color(18, 150, 219), null, null, null, Font.BOLD)
    );
}
