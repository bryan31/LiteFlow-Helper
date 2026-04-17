package com.yomahub.liteflowhelper.utils;

import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LiteFlow EL 表达式解析工具
 */
public class LiteFlowElParser {

    /**
     * 用于查找字符串字面量、注释和占位符的正则表达式
     * 匹配优先级：字符串 > 占位符 > 注释
     */
    private static final Pattern MIXED_PATTERN = Pattern.compile(
            "(\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"|'([^'\\\\]*(\\\\.[^'\\\\]*)]*)')|" +  // 字符串
            "(\\{\\{[^}]*\\}\\})|" +  // 占位符 {{...}}
            "//[^\\n]*|" +  // 行注释
            "/\\*.*?\\*/",   // 块注释
            Pattern.DOTALL);

    /** 占位符分组索引 */
    private static final int PLACEHOLDER_GROUP_INDEX = 6;
    
    /** 占位符替换前缀，用于生成合法的变量名 */
    public static final String DUMMY_VAR_PREFIX = "__ph";

    /**
     * 判断一个变量名是否为占位符替换后的 dummy 变量。
     */
    public static boolean isDummyPlaceholderVar(String varName) {
        return varName != null && varName.startsWith(DUMMY_VAR_PREFIX);
    }

    public enum TokenType {
        STRING,
        PLACEHOLDER,
        COMMENT
    }

    public static class ElToken {
        public final TokenType type;
        public final TextRange range;
        public final String text;

        public ElToken(TokenType type, TextRange range, String text) {
            this.type = type;
            this.range = range;
            this.text = text;
        }
    }

    public static class MaskedResult {
        public final String maskedText;
        public final List<ElToken> tokens;

        public MaskedResult(String maskedText, List<ElToken> tokens) {
            this.maskedText = maskedText;
            this.tokens = tokens;
        }
    }

    /**
     * 解析并屏蔽 EL 表达式中的注释和占位符
     *
     * @param expressionText 原始 EL 表达式
     * @return 包含屏蔽后文本和提取的 token 列表的结果
     */
    public static MaskedResult parse(String expressionText) {
        if (expressionText == null) {
            return new MaskedResult("", new ArrayList<>());
        }

        StringBuilder maskedExpressionBuilder = new StringBuilder(expressionText);
        List<ElToken> tokens = new ArrayList<>();
        Matcher matcher = MIXED_PATTERN.matcher(expressionText);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            TextRange range = new TextRange(start, end);
            String matchedText = expressionText.substring(start, end);

            if (matcher.group(1) != null) {
                // 字符串：保留不屏蔽
                tokens.add(new ElToken(TokenType.STRING, range, matchedText));
                continue;
            }

            if (matcher.group(PLACEHOLDER_GROUP_INDEX) != null) {
                // 占位符 {{...}}
                // [核心修改] 将占位符替换为等长的合法变量名 (例如 __ph_______)
                // 这样可以保留表达式结构，同时让 QLExpress 能够解析
                // 1. 对于 {{x}}=y，解析为 __ph___=y，y 能被正确解析为变量
                // 2. 对于 THEN(..., {{x}})，解析为 THEN(..., __ph___)，无需处理前面的逗号和注释
                tokens.add(new ElToken(TokenType.PLACEHOLDER, range, matchedText));
                replacePlaceholderWithDummyVar(maskedExpressionBuilder, start, end);
            } else {
                // 注释：替换为空格
                tokens.add(new ElToken(TokenType.COMMENT, range, matchedText));
                replaceRangeWithSpaces(expressionText, maskedExpressionBuilder, start, end);
            }
        }

        return new MaskedResult(maskedExpressionBuilder.toString(), tokens);
    }

    /**
     * 检查屏蔽后的表达式是否为空或无效
     *
     * @param maskedText 屏蔽后的表达式
     * @return 如果表达式为空或只包含空白字符/标点符号，返回 true
     */
    public static boolean isMaskedExpressionEmpty(String maskedText) {
        if (maskedText == null) {
            return true;
        }
        String trimmed = maskedText.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        // 检查是否只包含空白字符和标点符号（允许单个分号，这可能是有效的空语句）
        String contentOnly = trimmed.replaceAll("[\\s,;]+", "");
        return contentOnly.isEmpty();
    }

    /**
     * 将占位符替换为合法的变量名（等长）
     * 例如：{{abc}} (长度7) -> __ph___ (长度7)
     */
    private static void replacePlaceholderWithDummyVar(StringBuilder builder, int start, int end) {
        int length = end - start;
        if (length <= 0) return;

        // 构建 dummy 变量名
        // 确保以字母或下划线开头，且不包含特殊字符
        StringBuilder dummy = new StringBuilder();
        
        // 复制前缀
        dummy.append(DUMMY_VAR_PREFIX);
        
        // 填充剩余长度
        while (dummy.length() < length) {
            dummy.append('_');
        }
        
        // 如果原字符串太短（虽然 {{}} 至少2字符，__ph 至少4字符），截断
        // 实际上 {{}} 至少 2 字符，但通常会有内容。如果真的只有 {{}} (2 chars)，替换为 __
        String replacement;
        if (length < DUMMY_VAR_PREFIX.length()) {
             // 极短情况，全部用下划线
             StringBuilder shortDummy = new StringBuilder();
             for(int i=0; i<length; i++) shortDummy.append('_');
             replacement = shortDummy.toString();
        } else {
            replacement = dummy.substring(0, length);
        }

        builder.replace(start, end, replacement);
    }

    /**
     * 将指定范围内的字符替换为空格（保留换行符）
     */
    private static void replaceRangeWithSpaces(String text, StringBuilder builder, int start, int end) {
        for (int i = start; i < end && i < text.length(); i++) {
            char c = text.charAt(i);
            builder.setCharAt(i, c == '\n' ? '\n' : ' ');
        }
    }
}
