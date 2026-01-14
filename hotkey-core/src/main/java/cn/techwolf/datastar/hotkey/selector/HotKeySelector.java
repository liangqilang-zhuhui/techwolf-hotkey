package cn.techwolf.datastar.hotkey.selector;

import lombok.extern.slf4j.Slf4j;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.recorder.IAccessRecorder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 热Key选择器实现（模块四）
 * 职责：根据访问统计选择哪些key应该晋升为热Key或从热Key中移除
 * 设计：无状态，只负责计算和选择，不维护热Key列表
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class HotKeySelector implements IHotKeySelector {
    /**
     * 访问记录器
     */
    private final IAccessRecorder accessRecorder;

    /**
     * 配置参数
     */
    private final HotKeyConfig config;

    public HotKeySelector(IAccessRecorder accessRecorder, HotKeyConfig config) {
        this.accessRecorder = accessRecorder;
        this.config = config;
    }

    /**
     * 晋升热Key（每5秒执行一次）
     * 根据访问统计计算哪些key应该晋升为热Key
     * 将QPS top 10的热key，并且每秒访问大于3000，同时满足，则晋升为热key
     * 注意：定时任务由SchedulerManager管理，这里不添加@Scheduled注解
     */
    @Override
    public Set<String> promoteHotKeys(Set<String> currentHotKeys) {
        try {
            long startTime = System.currentTimeMillis();

            // 复用AccessRecorder的逻辑，获取所有满足QPS阈值的key
            Set<String> allHotKeysByQps = accessRecorder.getHotKeys();
            
            // 获取访问统计信息（用于排序）
            Map<String, Double> accessStats = accessRecorder.getAccessStatistics();

            double hotKeyQpsThreshold = config.getDetection().getHotKeyQpsThreshold();
            int topN = config.getDetection().getTopN();

            if (log.isDebugEnabled()) {
                log.debug("开始晋升热Key检查, 阈值: {}, TopN: {}, 满足阈值key数: {}, 当前管理热Key数: {}", 
                        hotKeyQpsThreshold, topN, allHotKeysByQps.size(), 
                        currentHotKeys != null ? currentHotKeys.size() : 0);
                // 输出前10个满足阈值的key的QPS信息
                allHotKeysByQps.stream()
                        .filter(key -> accessStats.containsKey(key))
                        .sorted((a, b) -> Double.compare(
                                accessStats.getOrDefault(b, 0.0), 
                                accessStats.getOrDefault(a, 0.0)
                        ))
                        .limit(10)
                        .forEach(key -> log.debug("访问统计: key={}, QPS={}", 
                                key, accessStats.get(key)));
            }

            // 从满足阈值的key中，按QPS降序排序，取TopN
            List<String> candidates = allHotKeysByQps.stream()
                    .filter(key -> {
                        Double qps = accessStats.get(key);
                        return qps != null && qps >= hotKeyQpsThreshold;
                    })
                    .sorted((a, b) -> Double.compare(
                            accessStats.getOrDefault(b, 0.0), 
                            accessStats.getOrDefault(a, 0.0)
                    ))
                    .limit(topN)
                    .collect(Collectors.toList());

            if (log.isDebugEnabled()) {
                log.debug("满足条件的候选key数: {}, 候选key列表: {}", 
                        candidates.size(), candidates);
                // 输出候选key的详细信息
                candidates.forEach(key -> {
                    Double qps = accessStats.get(key);
                    boolean isCurrentHotKey = currentHotKeys != null && currentHotKeys.contains(key);
                    log.debug("候选key: key={}, QPS={}, 是否已是热Key: {}", 
                            key, qps, isCurrentHotKey);
                });
            }

            // 获取新晋升的热key（排除已经在HotKeyManager中管理的key）
            Set<String> newHotKeys = candidates.stream()
                    .filter(key -> currentHotKeys == null || !currentHotKeys.contains(key))
                    .collect(Collectors.toSet());

            long cost = System.currentTimeMillis() - startTime;
            if (newHotKeys.size() > 0) {
                if (log.isDebugEnabled()) {
                    log.debug("晋升热Key计算完成, 新晋升: {}, 新晋升key列表: {}, 耗时: {}ms",
                            newHotKeys.size(), newHotKeys, cost);
                }
            } else if (log.isDebugEnabled()) {
                if (candidates.size() > 0) {
                    log.debug("所有候选key都已经是热Key，无需晋升, 候选key数: {}, 耗时: {}ms", 
                            candidates.size(), cost);
                } else {
                    log.debug("没有满足条件的候选key, 满足阈值key数: {}, 耗时: {}ms", 
                            allHotKeysByQps.size(), cost);
                }
            }
            return newHotKeys;
        } catch (Exception e) {
            log.error("晋升热Key计算失败", e);
            return Collections.emptySet();
        }
    }

    /**
     * 降级和淘汰（每分钟执行一次）
     * 根据访问统计计算哪些key应该从热Key中移除
     * 1. 热key中访问小于3000的，从热key移除
     * 2. 如果容量超限，则LRU进行淘汰访问量最低的key
     * 注意：定时任务由SchedulerManager管理，这里不添加@Scheduled注解
     */
    @Override
    public Set<String> demoteAndEvict(Set<String> currentHotKeys) {
        try {
            long startTime = System.currentTimeMillis();

            if (currentHotKeys == null || currentHotKeys.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("当前热Key列表为空，无需降级");
                }
                return Collections.emptySet();
            }

            Set<String> removedKeys = new HashSet<>();

            // 1. 热key中访问小于3000的，从热key移除
            double hotKeyQpsThreshold = config.getDetection().getHotKeyQpsThreshold();
            Map<String, Double> accessStats = accessRecorder.getAccessStatistics();

            Set<String> keysToRemove = new HashSet<>();
            for (String hotKey : currentHotKeys) {
                Double qps = accessStats.get(hotKey);
                if (qps == null || qps < hotKeyQpsThreshold) {
                    keysToRemove.add(hotKey);
                }
            }

            // 2. 如果容量超限，则清理低QPS的key（复用AccessRecorder的清理逻辑）
            int maxCapacity = config.getRecorder().getMaxCapacity();
            int currentSize = accessRecorder.size();

            if (currentSize > maxCapacity) {
                // 复用AccessRecorder的清理逻辑，自动保留热Key和温Key
                accessRecorder.cleanupLowQpsKeys();
                if (log.isDebugEnabled()) {
                    log.debug("容量超限清理完成, 清理前容量: {}, 清理后容量: {}", 
                            currentSize, accessRecorder.size());
                }
            }

            removedKeys.addAll(keysToRemove);

            long cost = System.currentTimeMillis() - startTime;
            if (log.isDebugEnabled()) {
                log.debug("降级和淘汰计算完成, 移除热Key: {}, 当前热Key数: {}, 耗时: {}ms",
                        removedKeys.size(), currentHotKeys.size(), cost);
            }

            return removedKeys;
        } catch (Exception e) {
            log.error("降级和淘汰计算失败", e);
            return Collections.emptySet();
        }
    }
}
