package com.yomahub.liteflowhelper.toolwindow.model;

/**
 * 节点的粗粒度类型类别，用于 EL 语义校验。
 * <p>
 * 将 {@link NodeType}（以及框架的 {@code NodeTypeEnum}）归并为校验关心的几类：
 * 脚本变体归入对应基础类（如 SWITCH_SCRIPT → {@link #SWITCH}）。
 * {@link #FALLBACK} 与 {@link #UNKNOWN} 在所有类型检查中均放行。
 * </p>
 */
public enum NodeCategory {
    COMMON,
    SWITCH,
    BOOLEAN,
    FOR,
    ITERATOR,
    FALLBACK,
    UNKNOWN
}
