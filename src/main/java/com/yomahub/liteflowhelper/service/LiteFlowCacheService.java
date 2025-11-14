package com.yomahub.liteflowhelper.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.Service;
import com.yomahub.liteflowhelper.toolwindow.model.ChainInfo;
import com.yomahub.liteflowhelper.toolwindow.model.LiteFlowNodeInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 项目级别的服务，用于缓存 LiteFlow 的 Chains 和 Nodes 信息。
 * <p>
 * 这个服务在项目加载时创建，并在项目关闭时销毁。它提供了一个单例的缓存实例，
 * 避免了在工具窗口的生命周期内重复扫描。
 * </p>
 * <p>
 * [性能优化] 使用不可变列表+volatile引用 + Map索引的方式，提升查询性能和线程安全性。
 * </p>
 */
@Service(Service.Level.PROJECT)
public final class LiteFlowCacheService {

    // 使用 volatile 保证可见性，通过替换引用而非修改内容来更新缓存
    private volatile List<ChainInfo> cachedChains = Collections.emptyList();
    private volatile List<LiteFlowNodeInfo> cachedNodes = Collections.emptyList();

    // 添加 Map 索引以加速查询，从 O(n) 优化到 O(1)
    private volatile Map<String, ChainInfo> chainMap = new ConcurrentHashMap<>();
    private volatile Map<String, LiteFlowNodeInfo> nodeMap = new ConcurrentHashMap<>();

    // 缓存更新时间戳，用于调试和监控
    private volatile long lastUpdateTime = 0L;

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
     * @return 一个不可变的 ChainInfo 列表（直接返回，无需复制）
     */
    public List<ChainInfo> getCachedChains() {
        return cachedChains; // 已经是不可变列表，直接返回
    }

    /**
     * [性能优化] 使用 Map 索引快速查询，复杂度从 O(n) 降至 O(1)
     */
    public boolean containsCachedChain(String chainId) {
        return chainMap.containsKey(chainId);
    }

    /**
     * 获取缓存的 LiteFlowNodeInfo 列表。
     *
     * @return 一个不可变的 LiteFlowNodeInfo 列表（直接返回，无需复制）
     */
    public List<LiteFlowNodeInfo> getCachedNodes() {
        return cachedNodes; // 已经是不可变列表，直接返回
    }

    /**
     * [性能优化] 使用 Map 索引快速查询，复杂度从 O(n) 降至 O(1)
     */
    public boolean containsCachedNode(String nodeId) {
        return nodeMap.containsKey(nodeId);
    }

    /**
     * 使用新的数据更新缓存。
     * [性能优化] 通过替换不可变列表引用，避免 CopyOnWriteArrayList 的高昂复制成本。
     * 同时更新 Map 索引以支持快速查询。
     *
     * @param chains 新的 ChainInfo 列表
     * @param nodes  新的 LiteFlowNodeInfo 列表
     */
    public void updateCache(List<ChainInfo> chains, List<LiteFlowNodeInfo> nodes) {
        // 构建新的 Map 索引
        Map<String, ChainInfo> newChainMap = new ConcurrentHashMap<>();
        Map<String, LiteFlowNodeInfo> newNodeMap = new ConcurrentHashMap<>();

        // 更新 chains
        if (chains != null && !chains.isEmpty()) {
            chains.forEach(chain -> newChainMap.put(chain.getName(), chain));
            this.cachedChains = Collections.unmodifiableList(chains);
        } else {
            this.cachedChains = Collections.emptyList();
        }

        // 更新 nodes
        if (nodes != null && !nodes.isEmpty()) {
            nodes.forEach(node -> newNodeMap.put(node.getNodeId(), node));
            this.cachedNodes = Collections.unmodifiableList(nodes);
        } else {
            this.cachedNodes = Collections.emptyList();
        }

        // 原子性地替换 Map 引用
        this.chainMap = newChainMap;
        this.nodeMap = newNodeMap;

        // 更新时间戳
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 清除所有缓存数据。
     * 当需要强制重新扫描时调用此方法。
     */
    public void clearCache() {
        this.cachedChains = Collections.emptyList();
        this.cachedNodes = Collections.emptyList();
        this.chainMap = new ConcurrentHashMap<>();
        this.nodeMap = new ConcurrentHashMap<>();
        this.lastUpdateTime = 0L;
    }

    /**
     * 检查缓存是否为空。
     *
     * @return 如果 chains 和 nodes 缓存都为空，则返回 true
     */
    public boolean isCacheEmpty() {
        return this.cachedChains.isEmpty() && this.cachedNodes.isEmpty();
    }

    /**
     * 获取缓存最后更新时间。
     *
     * @return 最后更新时间的时间戳（毫秒），如果从未更新则返回 0
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * 判断缓存是否过期。
     *
     * @param maxAgeMs 最大缓存时间（毫秒）
     * @return 如果缓存时间超过指定时长则返回 true
     */
    public boolean isCacheStale(long maxAgeMs) {
        return lastUpdateTime == 0L || System.currentTimeMillis() - lastUpdateTime > maxAgeMs;
    }
}
