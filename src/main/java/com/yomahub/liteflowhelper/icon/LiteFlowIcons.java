package com.yomahub.liteflowhelper.icon;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

/**
 * 插件中使用的图标常量
 *
 * @author Bryan.Zhang
 */
public class LiteFlowIcons {

    /**
     * Chain 图标
     */
    public static final Icon CHAIN_ICON = IconLoader.getIcon("/icons/chain.svg", LiteFlowIcons.class);

    /**
     * 普通组件图标
     */
    public static final Icon COMMON_COMPONENT_ICON = IconLoader.getIcon("/icons/common.svg", LiteFlowIcons.class);

    /**
     * 脚本组件图标
     */
    public static final Icon SCRIPT_COMPONENT_ICON = IconLoader.getIcon("/icons/script_common.svg", LiteFlowIcons.class);

    /**
     * LiteFlow EL 关键字图标
     */
    public static final Icon EL_KEYWORD_ICON = IconLoader.getIcon("/icons/xml.svg", LiteFlowIcons.class);
}
