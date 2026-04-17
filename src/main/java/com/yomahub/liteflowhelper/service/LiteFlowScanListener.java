package com.yomahub.liteflowhelper.service;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * LiteFlow 扫描事件监听器。
 */
public interface LiteFlowScanListener {

    Topic<LiteFlowScanListener> TOPIC = Topic.create("LiteFlow scan listener", LiteFlowScanListener.class);

    default void scanStarted(@NotNull LiteFlowScanTrigger trigger) {
    }

    default void scanFinished(@NotNull LiteFlowScanTrigger trigger) {
    }
}
