package com.yomahub.liteflowhelper.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.Service;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * 项目级别的服务，用于缓存 LiteFlow 的 Chains 和 Nodes 信息。
 * <p>
 * 这个服务在项目加载时创建，并在项目关闭时销毁。它提供了一个单例的缓存实例，
 * 避免了在工具窗口的生命周期内重复扫描。
 * </p>
 */
@Service(Service.Level.PROJECT)
public final class LiteFlowCacheService {

    // 使用线程安全的列表来存储缓存数据，以防并发修改
    private List<ChainInfo> cachedChains = new CopyOnWriteArrayList<>();
    private List<LiteFlowNodeInfo> cachedNodes = new CopyOnWriteArrayList<>();

    /**
     * 获取当前项目的 LiteFlowCacheService 实例。
     *
     * @param project 当前项目
     * @return 服务实例
     */
    public static LiteFlowCacheService getInstance(@NotNull Project project) {
        return project.getService(LiteFlowCacheService.class);
    }

    /**
     * 获取缓存的 ChainInfo 列表。
     *
     * @return 一个不可变的 ChainInfo 列表副本
     */
    public List<ChainInfo> getCachedChains() {
        return Collections.unmodifiableList(cachedChains);
    }

    public boolean containsCachedChain(String chainId) {
        return cachedChains.stream().anyMatch(chainInfo -> chainInfo.getName().equals(chainId));
    }

    /**
     * 获取缓存的 LiteFlowNodeInfo 列表。
     *
     * @return 一个不可变的 LiteFlowNodeInfo 列表副本
     */
    public List<LiteFlowNodeInfo> getCachedNodes() {
        return Collections.unmodifiableList(cachedNodes);
    }

    public boolean containsCachedNode(String nodeId) {
        return cachedNodes.stream().anyMatch(liteFlowNodeInfo -> liteFlowNodeInfo.getNodeId().equals(nodeId));
    }

    /**
     * 使用新的数据更新缓存。
     *
     * @param chains 新的 ChainInfo 列表
     * @param nodes  新的 LiteFlowNodeInfo 列表
     */
    public void updateCache(List<ChainInfo> chains, List<LiteFlowNodeInfo> nodes) {
        this.cachedChains.clear();
        if (chains != null) {
            this.cachedChains.addAll(chains);
        }

        this.cachedNodes.clear();
        if (nodes != null) {
            this.cachedNodes.addAll(nodes);
        }
    }

    /**
     * 清除所有缓存数据。
     * 当需要强制重新扫描时调用此方法。
     */
    public void clearCache() {
        this.cachedChains.clear();
        this.cachedNodes.clear();
    }

    /**
     * 检查缓存是否为空。
     *
     * @return 如果 chains 和 nodes 缓存都为空，则返回 true
     */
    public boolean isCacheEmpty() {
        return this.cachedChains.isEmpty() && this.cachedNodes.isEmpty();
    }
}
