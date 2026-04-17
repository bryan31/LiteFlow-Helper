package com.yomahub.liteflowhelper.highlight;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;

import java.awt.*;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * LiteFlow 插件高亮颜色定义
 *
 * @author Bryan.Zhang
 */
public class LiteFlowHighlightColorSettings {

        // 组件的高亮: Dark(#78ccf0), Light(#047CA8), 加粗
        public static final TextAttributesKey COMPONENT_KEY = createTextAttributesKey(
                        "LITEFLOW_COMPONENT",
                        new TextAttributes(new JBColor(new Color(4, 124, 168), new Color(120, 204, 240)), null, null,
                                        null, Font.BOLD));

        // 子流程的高亮: Dark(#3d8beb), Light(#0052CC), 加粗
        public static final TextAttributesKey CHAIN_KEY = createTextAttributesKey(
                        "LITEFLOW_CHAIN",
                        new TextAttributes(new JBColor(new Color(24, 0, 204), new Color(102, 61, 235)), null, null,
                                        null, Font.BOLD));

        // 子变量的高亮: Dark(#40BF77), Light(#008040), 加粗
        public static final TextAttributesKey SUB_VARIABLE_KEY = createTextAttributesKey(
                        "LITEFLOW_SUB_VARIABLE",
                        new TextAttributes(new JBColor(new Color(0, 128, 64), new Color(64, 191, 119)), null, null,
                                        null, Font.BOLD));

        // 异常组件的高亮: Dark(#E77F7F), Light(#D90000), 加粗, 下波浪线
        public static final TextAttributesKey UNKNOWN_COMPONENT_KEY = createTextAttributesKey(
                        "LITEFLOW_UNKNOWN_COMPONENT",
                        new TextAttributes(new JBColor(new Color(217, 0, 0), new Color(239, 67, 67)), null,
                                        new JBColor(new Color(217, 0, 0), new Color(239, 67, 67)),
                                        EffectType.WAVE_UNDERSCORE, Font.BOLD));

        // 新增: EL关键字的高亮: Dark(#f78b70), Light(#B34700), 加粗
        public static final TextAttributesKey EL_KEYWORD_KEY = createTextAttributesKey(
                        "LITEFLOW_EL_KEYWORD",
                        new TextAttributes(new JBColor(new Color(179, 71, 0), new Color(0xf7, 0x8b, 0x70)), null, null,
                                        null, Font.BOLD));

        // [ 新增 ] 匹配括号的高亮: Dark(#FFA500), Light(#CC8400), 带直角边框
        public static final TextAttributesKey MATCHED_BRACE_KEY = createTextAttributesKey(
                        "LITEFLOW_MATCHED_BRACE",
                        new TextAttributes(
                                        new JBColor(new Color(204, 132, 0), new Color(0xFF, 0xA5, 0x00)), // 前景色
                                        null, // 背景色
                                        new JBColor(new Color(204, 132, 0), new Color(0xFF, 0xA5, 0x00)), // 效果颜色
                                        EffectType.BOXED, // 效果类型
                                        Font.BOLD // 字体
                        ));

        // [ 新增 ] EL 注释的高亮: 灰色, 斜体
        public static final TextAttributesKey EL_COMMENT_KEY = createTextAttributesKey(
                        "LITEFLOW_EL_COMMENT",
                        new TextAttributes(new JBColor(new Color(0x80, 0x80, 0x80), new Color(0x80, 0x80, 0x80)), null,
                                        null, null, Font.ITALIC));
}
