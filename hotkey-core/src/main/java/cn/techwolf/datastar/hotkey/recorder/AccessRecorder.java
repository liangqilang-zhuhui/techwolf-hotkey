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
     * 访问统计信息（key -> AccessInfo）
     */
    private final ConcurrentHashMap<String, AccessInfo> accessStats;

    private int maxCapacity;
    /**
     * 配置参数
     */
    private final HotKeyConfig config;

    /**
     * 清理任务执行器（单线程，避免并发清理）
     */
    private static final ExecutorService cleanupExecutor = new ThreadPoolExecutor(
            1, 1, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "AccessRecorder-Cleanup-" + (++counter));
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 清理任务是否正在执行（避免并发执行多次清理）
     */
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);

    public AccessRecorder(HotKeyConfig config) {
        this.config = config;
        int maxCapacity = config.getRecorder().getMaxCapacity();
        this.accessStats = new ConcurrentHashMap<>(Math.min(maxCapacity, 10240));
        this.maxCapacity = config.getRecorder().getMaxCapacity();
    }

    @Override
    public void recordAccess(String key) {
        if (key == null) {
            return;
        }
        try {
            // 检查容量，如果超限则异步清理低QPS的key（不阻塞当前线程）
            int currentSize = accessStats.size();
            if (currentSize >= this.maxCapacity) {
                // 异步触发清理，避免阻塞记录访问的主流程
                cleanupLowQpsKeysAsync();
            }
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
     * 异步清理低QPS的key
     * 当容量超限时，异步执行清理任务，不阻塞调用线程
     */
    private void cleanupLowQpsKeysAsync() {
        // 如果已有清理任务在执行，跳过本次触发（避免并发执行）
        if (!cleanupInProgress.compareAndSet(false, true)) {
            return;
        }
        
        // 异步执行清理任务
        cleanupExecutor.execute(() -> {
            try {
                cleanupLowQpsKeys();
            } finally {
                // 清理完成，重置标志
                cleanupInProgress.set(false);
            }
        });
    }

    /**
     * 清理低QPS的key（实际执行清理逻辑）
     * 当容量超限时，按QPS排序，移除QPS最低的key
     * 保留热Key和温Key，优先清理冷Key
     * 清理策略：保留80%的容量，清理20%
     * 
     * 性能优化：
     * 1. 只遍历一次，同时更新QPS和收集候选key
     * 2. 使用部分排序（TopK）而不是全排序
     * 3. 使用PriorityQueue实现高效的TopK选择
     */
    @Override
    public void cleanupLowQpsKeys() {
        int currentSize = accessStats.size();
        
        if (currentSize < maxCapacity) {
            return;
        }
        
        try {
            // 计算需要清理的数量（保留80%的容量，清理20%）
            int targetSize = (int) (maxCapacity * 0.8);
            int toRemove = currentSize - targetSize;
            
            if (toRemove <= 0) {
                return;
            }
            
            long startTime = System.currentTimeMillis();
            
            // 温Key阈值（用于保护温Key，只清理冷Key）
            double warmKeyThreshold = config.getDetection().getWarmKeyQpsThreshold();
            
            // 使用PriorityQueue实现TopK选择（小顶堆，保留QPS最小的K个）
            // 优化：只遍历一次，同时更新QPS、判断保护状态和收集候选key
            PriorityQueue<Map.Entry<String, AccessInfo>> minHeap = new PriorityQueue<>(
                    toRemove + 1,
                    Comparator.comparingDouble(entry -> entry.getValue().getQps())
            );
            
            // 单次遍历：更新QPS并收集候选清理的key
            // 性能优化：避免多次遍历和重复更新QPS
            for (Map.Entry<String, AccessInfo> entry : accessStats.entrySet()) {
                AccessInfo info = entry.getValue();
                
                // 更新QPS（只更新一次）
                info.updateQps();
                double qps = info.getQps();
                
                // 跳过热Key（QPS >= 3000）和温Key（QPS >= 500），只清理冷Key
                if (qps >= warmKeyThreshold) {
                    continue;
                }
                
                // 使用小顶堆维护QPS最小的toRemove个key
                if (minHeap.size() < toRemove) {
                    minHeap.offer(entry);
                } else if (qps < minHeap.peek().getValue().getQps()) {
                    // 如果当前key的QPS更小，替换堆顶
                    minHeap.poll();
                    minHeap.offer(entry);
                }
            }
            
            // 移除低QPS的key
            int removedCount = 0;
            while (!minHeap.isEmpty()) {
                Map.Entry<String, AccessInfo> entry = minHeap.poll();
                String key = entry.getKey();
                if (accessStats.remove(key) != null) {
                    removedCount++;
                }
            }
            
            long cost = System.currentTimeMillis() - startTime;
            if (removedCount > 0) {
                if (log.isDebugEnabled()) {
                    log.debug("清理低QPS访问记录完成, 清理数量: {}, 清理前容量: {}, 清理后容量: {}, 耗时: {}ms", 
                            removedCount, currentSize, accessStats.size(), cost);
                }
            }
        } catch (Exception e) {
            log.warn("清理低QPS访问记录失败", e);
        }
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
