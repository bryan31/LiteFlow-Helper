package com.yomahub.liteflowhelper.highlight;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * LiteFlow 插件高亮颜色定义
 *
 * @author Bryan.Zhang
 */
public class LiteFlowHighlightColorSettings {

    // 组件的高亮: #78ccf0, 加粗
    public static final TextAttributesKey COMPONENT_KEY = createTextAttributesKey(
            "LITEFLOW_COMPONENT",
            new TextAttributes(new Color(120, 204, 240), null, null, null, Font.BOLD)
    );

    // 子流程的高亮: #3d8beb, 加粗
    public static final TextAttributesKey CHAIN_KEY = createTextAttributesKey(
            "LITEFLOW_CHAIN",
            new TextAttributes(new Color(61, 139, 235), null, null, null, Font.BOLD)
    );

    // 子变量的高亮: #40BF77, 加粗
    public static final TextAttributesKey SUB_VARIABLE_KEY = createTextAttributesKey(
            "LITEFLOW_SUB_VARIABLE",
            new TextAttributes(new Color(64, 191, 119), null, null, null, Font.BOLD)
    );

    // 异常组件的高亮: #E77F7F, 加粗, 下波浪线
    public static final TextAttributesKey UNKNOWN_COMPONENT_KEY = createTextAttributesKey(
            "LITEFLOW_UNKNOWN_COMPONENT",
            new TextAttributes(new Color(231, 127, 127), null, new Color(231, 127, 127), EffectType.WAVE_UNDERSCORE, Font.BOLD)
    );
}
