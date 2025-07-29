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
            new TextAttributes(new Color(239, 67, 67), null, new Color(239, 67, 67), EffectType.WAVE_UNDERSCORE, Font.BOLD)
    );

    // 新增: EL关键字的高亮: #f78b70, 加粗
    public static final TextAttributesKey EL_KEYWORD_KEY = createTextAttributesKey(
            "LITEFLOW_EL_KEYWORD",
            new TextAttributes(new Color(0xf7, 0x8b, 0x70), null, null, null, Font.BOLD)
    );

    // [ 新增 ] 匹配括号的高亮: 橘黄色 (#FFA500), 带直角边框
    public static final TextAttributesKey MATCHED_BRACE_KEY = createTextAttributesKey(
            "LITEFLOW_MATCHED_BRACE",
            new TextAttributes(
                    new Color(0xFF, 0xA5, 0x00), // 前景色: 橘黄色
                    null,                         // 背景色: null
                    new Color(0xFF, 0xA5, 0x00), // 效果颜色: 橘黄色
                    EffectType.BOXED,             // 效果类型: 直角边框
                    Font.BOLD                     // 字体: 加粗
            )
    );

    // [ 新增 ] EL 注释的高亮: 灰色, 斜体
    public static final TextAttributesKey EL_COMMENT_KEY = createTextAttributesKey(
            "LITEFLOW_EL_COMMENT",
            new TextAttributes(new Color(0x80, 0x80, 0x80), null, null, null, Font.ITALIC)
    );
}
