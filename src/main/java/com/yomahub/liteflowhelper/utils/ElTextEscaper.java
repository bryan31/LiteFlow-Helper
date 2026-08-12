package com.yomahub.liteflowhelper.utils;

import org.jetbrains.annotations.NotNull;

/**
 * EL 格式化文本写回 XML 文档前的转义工具（纯 Java，不依赖 IntelliJ 平台）。
 * <p>
 * 仅转义字符串字面量内的 {@code <}、{@code >}；{@code &} 仅在不成合法实体引用
 * （{@code &name;} / {@code &#123;}）时转义为 {@code &amp;}，避免已有实体被二次转义。
 * </p>
 */
public final class ElTextEscaper {

    private ElTextEscaper() {
    }

    public static @NotNull String escape(@NotNull String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        boolean inString = false;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < s.length()) {
                    sb.append(c).append(s.charAt(++i));
                    continue;
                }
                if (c == quote) {
                    inString = false;
                    sb.append(c);
                    continue;
                }
                if (c == '<') {
                    sb.append("&lt;");
                    continue;
                }
                if (c == '>') {
                    sb.append("&gt;");
                    continue;
                }
                if (c == '&' && !looksLikeEntity(s, i)) {
                    sb.append("&amp;");
                    continue;
                }
                sb.append(c);
            } else {
                if (c == '"' || c == '\'') {
                    inString = true;
                    quote = c;
                    sb.append(c);
                    continue;
                }
                if (c == '&' && !looksLikeEntity(s, i)) {
                    sb.append("&amp;");
                    continue;
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 判断 s[ampIdx] 的 '&' 是否为合法实体引用的开头（XML 预定义实体或数值实体）。 */
    private static boolean looksLikeEntity(@NotNull String s, int ampIdx) {
        int semi = s.indexOf(';', ampIdx + 1);
        if (semi < 0 || semi - ampIdx > 11 || semi == ampIdx + 1) {
            return false;
        }
        String body = s.substring(ampIdx + 1, semi);
        if (body.startsWith("#")) {
            // 数值实体：&#123; 或 &#x1F;
            for (int k = 1; k < body.length(); k++) {
                char c = body.charAt(k);
                boolean hex = body.length() > 1 && (body.charAt(1) == 'x' || body.charAt(1) == 'X');
                boolean ok = hex ? (Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))
                        : Character.isDigit(c);
                if (!ok && !(hex && k == 1)) {
                    return false;
                }
            }
            return body.length() > 1;
        }
        // XML 预定义实体只有这 5 个，其余名字必须转义 &
        return "lt".equals(body) || "gt".equals(body) || "amp".equals(body)
                || "quot".equals(body) || "apos".equals(body);
    }
}
