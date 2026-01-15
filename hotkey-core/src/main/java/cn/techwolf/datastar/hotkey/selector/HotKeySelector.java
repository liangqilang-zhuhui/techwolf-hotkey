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

            // 获取访问统计信息
            Map<String, Double> accessStats = accessRecorder.getAccessStatistics();
            Set<String> allHotKeysByQps = accessRecorder.getHotKeys();
            
            double hotKeyQpsThreshold = config.getDetection().getHotKeyQpsThreshold();
            int topN = config.getDetection().getTopN();

            // 记录开始日志
            logPromotionStart(allHotKeysByQps, accessStats, hotKeyQpsThreshold, topN, currentHotKeys);

            // 从满足阈值的key中，按QPS降序排序，取TopN
            List<String> candidates = selectTopNCandidates(allHotKeysByQps, accessStats, hotKeyQpsThreshold, topN);
            
            // 记录候选key信息
            logCandidates(candidates, accessStats, currentHotKeys);

            // 获取新晋升的热key（排除已经在HotKeyManager中管理的key）
            Set<String> newHotKeys = filterNewHotKeys(candidates, currentHotKeys);

            // 记录完成日志
            logPromotionResult(newHotKeys, candidates, allHotKeysByQps, startTime);
            
            return newHotKeys;
        } catch (Exception e) {
            log.error("晋升热Key计算失败", e);
            return Collections.emptySet();
        }
    }

    /**
     * 选择TopN候选key
     */
    private List<String> selectTopNCandidates(Set<String> allHotKeysByQps, 
                                             Map<String, Double> accessStats,
                                             double threshold, 
                                             int topN) {
        return allHotKeysByQps.stream()
                .filter(key -> {
                    Double qps = accessStats.get(key);
                    return qps != null && qps >= threshold;
                })
                .sorted((a, b) -> Double.compare(
                        accessStats.getOrDefault(b, 0.0), 
                        accessStats.getOrDefault(a, 0.0)
                ))
                .limit(topN)
                .collect(Collectors.toList());
    }

    /**
     * 过滤出新晋升的热key
     */
    private Set<String> filterNewHotKeys(List<String> candidates, Set<String> currentHotKeys) {
        return candidates.stream()
                .filter(key -> currentHotKeys == null || !currentHotKeys.contains(key))
                .collect(Collectors.toSet());
    }

    /**
     * 记录晋升开始日志
     */
    private void logPromotionStart(Set<String> allHotKeysByQps,
                                   Map<String, Double> accessStats,
                                   double threshold,
                                   int topN,
                                   Set<String> currentHotKeys) {
        if (log.isDebugEnabled()) {
            log.debug("开始晋升热Key检查, 阈值: {}, TopN: {}, 满足阈值key数: {}, 当前管理热Key数: {}", 
                    threshold, topN, allHotKeysByQps.size(), 
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
    }

    /**
     * 记录候选key信息
     */
    private void logCandidates(List<String> candidates, 
                              Map<String, Double> accessStats,
                              Set<String> currentHotKeys) {
        if (log.isDebugEnabled()) {
            log.debug("满足条件的候选key数: {}, 候选key列表: {}", 
                    candidates.size(), candidates);
            candidates.forEach(key -> {
                Double qps = accessStats.get(key);
                boolean isCurrentHotKey = currentHotKeys != null && currentHotKeys.contains(key);
                log.debug("候选key: key={}, QPS={}, 是否已是热Key: {}", 
                        key, qps, isCurrentHotKey);
            });
        }
    }

    /**
     * 记录晋升结果日志
     */
    private void logPromotionResult(Set<String> newHotKeys,
                                   List<String> candidates,
                                   Set<String> allHotKeysByQps,
                                   long startTime) {
        if (!log.isDebugEnabled()) {
            return;
        }
        long cost = System.currentTimeMillis() - startTime;
        if (newHotKeys.size() > 0) {
            log.debug("晋升热Key计算完成, 新晋升: {}, 新晋升key列表: {}, 耗时: {}ms",
                    newHotKeys.size(), newHotKeys, cost);
        } else if (candidates.size() > 0) {
            log.debug("所有候选key都已经是热Key，无需晋升, 候选key数: {}, 耗时: {}ms", 
                    candidates.size(), cost);
        } else {
            log.debug("没有满足条件的候选key, 满足阈值key数: {}, 耗时: {}ms", 
                    allHotKeysByQps.size(), cost);
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

            // 筛选出需要降级的key（QPS低于阈值）
            Set<String> removedKeys = selectKeysToDemote(currentHotKeys);

            // 记录结果日志
            logDemotionResult(removedKeys, currentHotKeys, startTime);

            return removedKeys;
        } catch (Exception e) {
            log.error("降级和淘汰计算失败", e);
            return Collections.emptySet();
        }
    }

    /**
     * 筛选出需要降级的key（QPS低于阈值）
     */
    private Set<String> selectKeysToDemote(Set<String> currentHotKeys) {
        double hotKeyQpsThreshold = config.getDetection().getHotKeyQpsThreshold();
        Map<String, Double> accessStats = accessRecorder.getAccessStatistics();

        Set<String> keysToRemove = new HashSet<>();
        for (String hotKey : currentHotKeys) {
            Double qps = accessStats.get(hotKey);
            if (qps == null || qps < hotKeyQpsThreshold) {
                keysToRemove.add(hotKey);
            }
        }
        return keysToRemove;
    }

    /**
     * 记录降级结果日志
     */
    private void logDemotionResult(Set<String> removedKeys, Set<String> currentHotKeys, long startTime) {
        if (log.isDebugEnabled()) {
            long cost = System.currentTimeMillis() - startTime;
            log.debug("降级和淘汰计算完成, 移除热Key: {}, 当前热Key数: {}, 耗时: {}ms",
                    removedKeys.size(), currentHotKeys.size(), cost);
        }
    }
}
