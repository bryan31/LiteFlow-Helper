package com.yomahub.liteflowhelper.utils;

/**
 * EL 校验产出的一条问题（结构或语义），携带严重级别、在源文本中的偏移区间与描述。
 * 偏移相对传入 {@link LiteFlowElAnalyzer#analyze} 的 EL 文本。
 */
public final class ElValidationFinding {

    public enum Severity { ERROR, WARNING }

    public final Severity severity;
    public final int start;
    public final int end;
    public final String message;

    public ElValidationFinding(Severity severity, int start, int end, String message) {
        this.severity = severity;
        this.start = start;
        this.end = end;
        this.message = message;
    }

    @Override
    public String toString() {
        return severity + "[" + start + "," + end + "): " + message;
    }
}
