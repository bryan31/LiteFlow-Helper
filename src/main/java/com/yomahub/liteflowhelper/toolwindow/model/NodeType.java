package com.yomahub.liteflowhelper.toolwindow.model;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

/**
 * 表示不同类型的 LiteFlow 节点及其关联图标的枚举。
 */
public enum NodeType {
    // 继承式组件节点
    COMMON_COMPONENT("com.yomahub.liteflow.core.NodeComponent", "CM", "/icons/common.svg"),
    SWITCH_COMPONENT("com.yomahub.liteflow.core.NodeSwitchComponent", "SWI", "/icons/common.svg"),
    BOOLEAN_COMPONENT("com.yomahub.liteflow.core.NodeBooleanComponent", "BOL", "/icons/common.svg"),
    FOR_COMPONENT("com.yomahub.liteflow.core.NodeForComponent", "FOR", "/icons/common.svg"),
    ITERATOR_COMPONENT("com.yomahub.liteflow.core.NodeIteratorComponent", "ITR", "/icons/common.svg"),

    // XML脚本节点
    SCRIPT_COMMON("script", "CM", "/icons/script_common.svg"),
    SCRIPT_SWITCH("switch_script", "SWI", "/icons/script_common.svg"),
    SCRIPT_BOOLEAN("boolean_script", "BOL", "/icons/script_common.svg"),
    SCRIPT_FOR("for_script", "FOR", "/icons/script_common.svg"),

    // 声明式节点类型 - 对应 com.yomahub.liteflow.enums.NodeTypeEnum
    DECLARATIVE_COMMON("COMMON", "CM", "/icons/common.svg"), // 请替换为实际图标路径
    DECLARATIVE_SWITCH("SWITCH", "SWI", "/icons/common.svg"), // 请替换为实际图标路径
    DECLARATIVE_BOL("BOOLEAN", "BOL", "/icons/common.svg"),         // 请替换为实际图标路径
    DECLARATIVE_FOR("FOR", "FOR", "/icons/common.svg"),       // 请替换为实际图标路径
    DECLARATIVE_ITERATOR("ITERATOR", "ITR", "/icons/common.svg"),   // 请替换为实际图标路径
    // 可根据需要添加更多 NodeTypeEnum 中的类型

    UNKNOWN("unknown", "未知节点", null); // 兜底类型

    private final String identifier; // 标识符 (全限定类名、XML的type属性值或NodeTypeEnum的名称)
    private final String description; // 描述 (用于UI显示)
    private final String iconPath;    // 图标路径
    private Icon icon;                // 缓存加载后的图标

    NodeType(String identifier, String description, String iconPath) {
        this.identifier = identifier;
        this.description = description;
        this.iconPath = iconPath;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getDescription() {
        return description;
    }

    public Icon getIcon() {
        if (icon == null && iconPath != null) {
            try {
                // 使用 NodeType.class 作为类加载器来查找资源文件
                icon = IconLoader.getIcon(iconPath, NodeType.class);
            } catch (Exception e) {
                System.err.println("加载图标失败: " + iconPath + ", 错误: " + e.getMessage());
                // 可选: 在这里加载一个默认的错误图标
            }
        }
        return icon; // 如果 iconPath 为 null 或加载失败，可能返回 null
    }

    /**
     * 根据组件的父类全限定名获取节点类型 (用于继承式组件)
     * @param fqClassName 父类的全限定名
     * @return 对应的 NodeType，如果找不到则返回 UNKNOWN
     */
    public static NodeType fromComponentClass(String fqClassName) {
        if (fqClassName == null) return UNKNOWN;
        for (NodeType type : values()) {
            // 检查是否为继承式组件类型 (通过枚举名称约定) 并且标识符匹配
            if (type.name().endsWith("_COMPONENT") && fqClassName.equals(type.getIdentifier())) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * 根据 XML中的 type 属性值获取节点类型 (用于XML脚本节点)
     * @param xmlTypeAttr XML <node> 标签的 type 属性值
     * @return 对应的 NodeType，如果找不到则返回 UNKNOWN
     */
    public static NodeType fromXmlType(String xmlTypeAttr) {
        if (xmlTypeAttr == null) return UNKNOWN;
        for (NodeType type : values()) {
            // 检查是否为脚本类型 (通过枚举名称约定) 并且标识符匹配
            if (type.name().startsWith("SCRIPT_") && xmlTypeAttr.equals(type.getIdentifier())) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * 根据声明式组件中 @LiteflowMethod 的 nodeType (com.yomahub.liteflow.enums.NodeTypeEnum 的名称) 获取节点类型
     * @param liteFlowNodeTypeName NodeTypeEnum 的 .name() 值 (例如 "COMMON", "SWITCH")
     * @return 对应的 NodeType，如果找不到则返回 UNKNOWN
     */
    public static NodeType fromDeclarativeNodeType(String liteFlowNodeTypeName) {
        if (liteFlowNodeTypeName == null || liteFlowNodeTypeName.trim().isEmpty()) {
            // 如果注解中 nodeType 未指定，LiteFlow 默认为 COMMON
            return DECLARATIVE_COMMON;
        }
        for (NodeType type : values()) {
            // 检查是否为声明式节点类型 (通过枚举名称约定) 并且标识符 (NodeTypeEnum的名称) 匹配
            if (type.name().startsWith("DECLARATIVE_") && liteFlowNodeTypeName.equalsIgnoreCase(type.getIdentifier())) {
                return type;
            }
        }
        // 如果 liteFlowNodeTypeName 是 "COMMON" 但上面没有匹配到 DECLARATIVE_COMMON (例如因为大小写或拼写)，
        // 确保有一个回退机制或更严格的匹配。
        // 目前，如果未直接匹配，则返回 UNKNOWN。
        // 如果 liteFlowNodeTypeName 为空或仅包含空格，则上面已处理为 DECLARATIVE_COMMON。
        return UNKNOWN;
    }
}
