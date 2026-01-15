package cn.techwolf.datastar.hotkey.recorder;

import lombok.extern.slf4j.Slf4j;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * 访问记录器实现（模块三）
 * 职责：记录温、热key数据
 * 规则：冷key需要每秒访问量大于500才可以升级到温key
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class AccessRecorder implements IAccessRecorder {
    /**
     * 容量保留比例（保留80%的容量）
     */
    private static final double CAPACITY_RETAIN_RATIO = 0.8;

    /**
     * 访问统计信息（key -> AccessInfo）
     */
    private final ConcurrentHashMap<String, AccessInfo> accessStats;

    /**
     * 最大容量
     */
    private final int maxCapacity;

    /**
     * 配置参数
     */
    private final HotKeyConfig config;

    /**
     * 非活跃key过期时间（毫秒）
     */
    private final long inactiveExpireTimeMs;

    /**
     * 清理任务是否正在执行（避免并发执行多次清理）
     */
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);

    public AccessRecorder(HotKeyConfig config) {
        this.config = config;
        this.maxCapacity = config.getRecorder().getMaxCapacity();
        this.accessStats = new ConcurrentHashMap<>(Math.min(maxCapacity, 10240));
        this.inactiveExpireTimeMs = config.getRecorder().getInactiveExpireTime() * 1000L;
    }

    @Override
    public void recordAccess(String key) {
        if (key == null) {
            return;
        }
        try {
            // 使用computeIfAbsent，避免额外的锁操作
            AccessInfo info = accessStats.computeIfAbsent(
                    key,
                    k -> new AccessInfo(k, config.getRecorder().getWindowSize())
            );
            info.recordAccess();
        } catch (Exception e) {
            log.debug("记录访问统计失败, key: {}", key, e);
        }
    }

    @Override
    public Map<String, Double> getAccessStatistics() {
        Map<String, Double> stats = new HashMap<>();
        accessStats.forEach((key, info) -> {
            info.updateQps();
            stats.put(key, info.getQps());
        });
        return stats;
    }

    @Override
    public Set<String> getWarmKeys() {
        double warmKeyThreshold = config.getDetection().getWarmKeyQpsThreshold();
        return accessStats.entrySet().stream()
                .filter(entry -> {
                    entry.getValue().updateQps();
                    double qps = entry.getValue().getQps();
                    return qps >= warmKeyThreshold && qps < config.getDetection().getHotKeyQpsThreshold();
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> getHotKeys() {
        double hotKeyThreshold = config.getDetection().getHotKeyQpsThreshold();
        return accessStats.entrySet().stream()
                .filter(entry -> {
                    entry.getValue().updateQps();
                    double qps = entry.getValue().getQps();
                    return qps >= hotKeyThreshold;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public int size() {
        return accessStats.size();
    }


    /**
     * 清理过期和低QPS的key（默认异步执行）
     * 外部调用此方法时，会异步执行清理任务，不阻塞调用线程
     */
    @Override
    public void cleanupKeys() {
        // 如果已有清理任务在执行，跳过本次触发（避免并发执行）
        if (!cleanupInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            cleanupKeysInternal();
        } finally {
            // 清理完成，重置标志
            cleanupInProgress.set(false);
        }
    }

    /**
     * 清理过期和低QPS的key（实际执行清理逻辑）
     * 包含两种触发机制：
     * 1. 时间触发：清理超过指定时间未访问的非活跃key
     * 2. 容量触发：当容量超限时，按QPS排序，移除QPS最低的key
     * 
     * 保留热Key和温Key，优先清理冷Key
     * 清理策略：保留80%的容量，清理20%
     */
    private void cleanupKeysInternal() {
        long startTime = System.currentTimeMillis();
        int initialSize = accessStats.size();
        
        try {
            // 第一部分：时间触发 - 清理过期key
            int expiredCount = cleanupExpiredKeys();
            
            // 第二部分：容量触发 - 清理低QPS key
            int currentSize = accessStats.size();
            int lowQpsCount = 0;
            if (currentSize >= maxCapacity) {
                lowQpsCount = cleanupLowQpsKeysByCapacity(currentSize);
            }
            
            // 记录清理结果
            if (expiredCount > 0 || lowQpsCount > 0) {
                long cost = System.currentTimeMillis() - startTime;
                log.info("清理key完成, 过期清理: {}, 低QPS清理: {}, 清理前容量: {}, 清理后容量: {}, 耗时: {}ms",
                        expiredCount, lowQpsCount, initialSize, accessStats.size(), cost);
            }
        } catch (Exception e) {
            log.error("清理key失败", e);
        }
    }

    /**
     * 时间触发：清理超过指定时间未访问的非活跃key
     * 
     * @return 清理的key数量
     */
    private int cleanupExpiredKeys() {
        long currentTime = System.currentTimeMillis();
        long expireThreshold = currentTime - inactiveExpireTimeMs;
        
        // 收集需要过期的key
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, AccessInfo> entry : accessStats.entrySet()) {
            AccessInfo info = entry.getValue();
            // 如果最后访问时间早于过期阈值，标记为需要移除
            if (info.getLastAccessTime() < expireThreshold) {
                expiredKeys.add(entry.getKey());
            }
        }
        
        // 移除过期的key
        int removedCount = 0;
        for (String key : expiredKeys) {
            if (accessStats.remove(key) != null) {
                removedCount++;
            }
        }
        
        return removedCount;
    }

    /**
     * 容量触发：当容量超限时，按QPS排序，移除QPS最低的key
     * 保留热Key和温Key，优先清理冷Key
     * 清理策略：保留80%的容量，清理20%
     * 
     * 性能优化：
     * 1. 只遍历一次，同时更新QPS和收集候选key
     * 2. 使用部分排序（TopK）而不是全排序
     * 3. 使用PriorityQueue实现高效的TopK选择
     * 
     * @param currentSize 当前容量
     * @return 清理的key数量
     */
    private int cleanupLowQpsKeysByCapacity(int currentSize) {
        int toRemove = calculateKeysToRemove(currentSize);
        if (toRemove <= 0) {
            return 0;
        }
        
        // 收集需要清理的低QPS key
        PriorityQueue<Map.Entry<String, AccessInfo>> minHeap = collectLowQpsKeys(toRemove);
        
        // 移除收集到的key
        return removeCollectedKeys(minHeap);
    }

    /**
     * 计算需要清理的key数量
     * 
     * @param currentSize 当前容量
     * @return 需要清理的key数量
     */
    private int calculateKeysToRemove(int currentSize) {
        int targetSize = (int) (maxCapacity * CAPACITY_RETAIN_RATIO);
        return currentSize - targetSize;
    }

    /**
     * 收集需要清理的低QPS key（使用小顶堆）
     * 
     * @param toRemove 需要清理的key数量
     * @return 包含需要清理key的小顶堆
     */
    private PriorityQueue<Map.Entry<String, AccessInfo>> collectLowQpsKeys(int toRemove) {
        double warmKeyThreshold = config.getDetection().getWarmKeyQpsThreshold();
        
        PriorityQueue<Map.Entry<String, AccessInfo>> minHeap = new PriorityQueue<>(
                toRemove + 1,
                Comparator.comparingDouble(entry -> entry.getValue().getQps())
        );
        
        // 单次遍历：更新QPS并收集候选清理的key
        for (Map.Entry<String, AccessInfo> entry : accessStats.entrySet()) {
            AccessInfo info = entry.getValue();
            info.updateQps();
            double qps = info.getQps();
            
            // 跳过热Key和温Key，只清理冷Key
            if (qps >= warmKeyThreshold) {
                continue;
            }
            
            // 使用小顶堆维护QPS最小的toRemove个key
            if (minHeap.size() < toRemove) {
                minHeap.offer(entry);
            } else if (qps < minHeap.peek().getValue().getQps()) {
                minHeap.poll();
                minHeap.offer(entry);
            }
        }
        
        return minHeap;
    }

    /**
     * 移除收集到的key
     * 
     * @param minHeap 包含需要清理key的小顶堆
     * @return 实际移除的key数量
     */
    private int removeCollectedKeys(PriorityQueue<Map.Entry<String, AccessInfo>> minHeap) {
        int removedCount = 0;
        while (!minHeap.isEmpty()) {
            Map.Entry<String, AccessInfo> entry = minHeap.poll();
            String key = entry.getKey();
            if (accessStats.remove(key) != null) {
                removedCount++;
            }
        }
        return removedCount;
    }

    /**
     * 访问信息
     */
    public static class AccessInfo {
        private final String key;
        private final LongAdder accessCount;
        private volatile long windowStartTime;
        private volatile long lastAccessTime;
        private volatile double qps;
        private final long windowSize;

        public AccessInfo(String key, long windowSize) {
            this.key = key;
            this.windowSize = windowSize;
            this.accessCount = new LongAdder();
            long currentTime = System.currentTimeMillis();
            this.windowStartTime = currentTime;
            this.lastAccessTime = currentTime;
            this.qps = 0.0;
        }

        public void recordAccess() {
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - windowStartTime;

            // 如果超过窗口大小，重置窗口
            if (elapsed >= windowSize * 1000) {
                resetWindow(currentTime);
            }

            accessCount.increment();
            lastAccessTime = currentTime;
        }

        private void resetWindow(long currentTime) {
            windowStartTime = currentTime;
            accessCount.reset();
            qps = 0.0;
        }

        public void updateQps() {
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - windowStartTime;

            if (elapsed > 0) {
                long count = accessCount.sum();
                qps = (count * 1000.0) / elapsed; // 次/秒
            } else {
                qps = 0.0;
            }
        }

        public String getKey() {
            return key;
        }

        public double getQps() {
            return qps;
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }

        public long getAccessCount() {
            return accessCount.sum();
        }
    }
}
