package com.yomahub.liteflowhelper.utils;

/**
 * EL 格式化选项。字段风格与 {@link LiteFlowElToken} 一致（public final）。
 */
public final class ElFormatOptions {

    /** 行宽预算（列），超过则按层级展开。 */
    public final int maxLineWidth;
    /** 每层嵌套的缩进空格数。 */
    public final int indentWidth;
    /** EL 续行的绝对缩进列（由 IDE 层根据标签位置计算）。 */
    public final int baseIndent;
    /** EL 首 token 在其所在行的起始列（用于首行宽度核算）。 */
    public final int firstLineColumn;

    public ElFormatOptions(int maxLineWidth, int indentWidth, int baseIndent, int firstLineColumn) {
        this.maxLineWidth = maxLineWidth;
        this.indentWidth = indentWidth;
        this.baseIndent = baseIndent;
        this.firstLineColumn = firstLineColumn;
    }

    /** 单元测试与默认场景的默认选项。 */
    public static ElFormatOptions defaults() {
        return new ElFormatOptions(80, 4, 0, 0);
    }
}
