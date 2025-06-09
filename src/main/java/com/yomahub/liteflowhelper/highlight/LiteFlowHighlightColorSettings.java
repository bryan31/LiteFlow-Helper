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

    // region --- 新增/修改的高亮定义 ---

    /**
     * 已识别的组件高亮 (#78ccf0, 粗体)
     */
    public static final TextAttributesKey COMPONENT_KEY = createTextAttributesKey(
            "LITEFLOW_COMPONENT",
            new TextAttributes(new Color(120, 204, 240), null, null, null, Font.BOLD)
    );

    /**
     * 已识别的子流程高亮 (#3d8beb, 粗体)
     */
    public static final TextAttributesKey SUB_CHAIN_KEY = createTextAttributesKey(
            "LITEFLOW_SUB_CHAIN",
            new TextAttributes(new Color(61, 139, 235), null, null, null, Font.BOLD)
    );

    /**
     * 已识别的子变量高亮 (#40BF77, 粗体)
     */
    public static final TextAttributesKey SUB_VARIABLE_KEY = createTextAttributesKey(
            "LITEFLOW_SUB_VARIABLE",
            new TextAttributes(new Color(64, 191, 119), null, null, null, Font.BOLD)
    );

    /**
     * 异常/未定义的组件高亮 (new Color(231, 127, 127), 波浪下划线)
     */
    public static final TextAttributesKey ERROR_KEY = createTextAttributesKey(
            "LITEFLOW_ERROR_COMPONENT",
            new TextAttributes(new Color(231, 127, 127), null, new Color(231, 127, 127), EffectType.WAVE_UNDERSCORE, Font.PLAIN)
    );

    // endregion

}
