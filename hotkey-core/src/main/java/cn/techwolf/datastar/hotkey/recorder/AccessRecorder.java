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
     * 容量告警阈值（超过此阈值时立即触发清理）
     */
    private static final double CAPACITY_ALERT_RATIO = 0.9;

    /**
     * 待晋升队列阈值最小值（防止阈值过低导致太多key进入访问统计表）
     */
    private static final int MIN_PROMOTION_QUEUE_THRESHOLD = 100;

    /**
     * 待晋升队列阈值：只有访问次数达到此值的key才进入访问统计表
     * 计算公式：promotionQueueThreshold = max(MIN_PROMOTION_QUEUE_THRESHOLD, (warmKeyThreshold * promotionInterval / 1000.0) )
     * 目的：过滤极低流量的key，减少内存占用
     * 设计理念：在晋升间隔内，如果访问次数达到温Key阈值，则进入访问统计表
     *         这样确保有足够流量潜力的key能进入晋升通道，同时过滤掉极低流量的key
     */
    private final int promotionQueueThreshold;

    /**
     * 待晋升队列（key -> Integer）：存储待晋升key的访问次数
     * 只有访问次数达到阈值的key才会升级到访问统计表（promotionQueue）
     * 使用轻量级的ConcurrentHashMap，只存储key和访问次数
     */
    private final ConcurrentHashMap<String, Integer> recentQpsTable;


    /**
     * 访问统计表（key -> AccessInfo）：存储QPS统计信息
     * 只有通过待晋升队列筛选的key才会存储在这里
     * 存储完整的AccessInfo对象，包含QPS计算所需的详细信息
     */
    private final ConcurrentHashMap<String, AccessInfo> promotionQueue;

    /**
     * 最大容量
     */
    private final int maxCapacity;

    private final int maxPromotionQueueCapacity = 1000000;

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

    /**
     * 待晋升队列清理任务是否正在执行（避免并发执行多次清理）
     */
    private final AtomicBoolean promotionQueueCleanupInProgress = new AtomicBoolean(false);

    public AccessRecorder(HotKeyConfig config) {
        this.config = config;
        this.maxCapacity = config.getRecorder().getMaxCapacity();
        this.recentQpsTable = new ConcurrentHashMap<>(Math.min(maxCapacity* 2, 20480));
        this.promotionQueue = new ConcurrentHashMap<>(Math.min(maxCapacity, 10240)); // 待晋升队列容量更大
        this.inactiveExpireTimeMs = config.getRecorder().getInactiveExpireTime() * 1000L;
        int calculatedThreshold = (int) ((config.getDetection().getWarmKeyQpsThreshold() * config.getDetection().getPromotionInterval() / 1000));
        this.promotionQueueThreshold = Math.max(MIN_PROMOTION_QUEUE_THRESHOLD, calculatedThreshold);
        log.info("待晋升队列阈值计算完成: 计算值={}, 最终阈值={}", calculatedThreshold, promotionQueueThreshold);
    }

    @Override
    public void recordAccess(String key) {
        if (key == null) {
            return;
        }
        try {
            // 先检查是否已经在访问统计表（promotionQueue）中
            AccessInfo info = promotionQueue.get(key);
            if (info != null) {
                // 已经在访问统计表中，直接记录访问
                info.recordAccess();
            } else {
                // 不在访问统计表中，先进入待晋升队列（recentQpsTable）
                int count = recentQpsTable.compute(key, (k, v) -> (v == null ? 0 : v) + 1);
                // 达到待晋升队列阈值，升级到访问统计表
                if (count >= promotionQueueThreshold) {
                    // 使用computeIfAbsent，避免并发创建
                    AccessInfo newInfo = promotionQueue.computeIfAbsent(
                            key,
                            k -> {
                                recentQpsTable.remove(k); // 从待晋升队列移除
                                return new AccessInfo(k, config.getRecorder().getWindowSize());
                            }
                    );
                    newInfo.recordAccess();
                }
            }
        } catch (Exception e) {
            log.debug("记录访问统计失败, key: {}", key, e);
        }
    }

    @Override
    public Map<String, Double> getAccessStatistics() {
        Map<String, Double> stats = new HashMap<>();
        promotionQueue.forEach((key, info) -> {
            info.updateQps();
            stats.put(key, info.getQps());
        });
        return stats;
    }
    @Override
    public Set<String> getHotKeys() {
        double hotKeyThreshold = config.getDetection().getHotKeyQpsThreshold();
        return promotionQueue.entrySet().stream()
                .filter(entry -> {
                    entry.getValue().updateQps();
                    double qps = entry.getValue().getQps();
                    return qps >= hotKeyThreshold;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public RecorderStatistics getStatistics() {
        // 计算 PromotionQueue 统计信息
        RecorderStatistics.ComponentStatistics promotionQueueStats = calculatePromotionQueueStatistics();
        
        // 计算 RecentQps 统计信息
        RecorderStatistics.ComponentStatistics recentQpsStats = calculateRecentQpsStatistics();
        
        return new RecorderStatistics(promotionQueueStats, recentQpsStats);
    }

    /**
     * 计算 PromotionQueue 的统计信息
     * 
     * @return PromotionQueue 统计信息
     */
    private RecorderStatistics.ComponentStatistics calculatePromotionQueueStatistics() {
        int size = promotionQueue.size();
        if (size == 0) {
            return new RecorderStatistics.ComponentStatistics(0, 0L, 0);
        }
        
        // 计算平均key长度（采样前100个）
        int sampleSize = Math.min(size, 100);
        int totalKeyLength = 0;
        int count = 0;
        for (String key : promotionQueue.keySet()) {
            if (count >= sampleSize) {
                break;
            }
            totalKeyLength += key != null ? key.length() : 0;
            count++;
        }
        int avgKeyLength = count > 0 ? totalKeyLength / count : 20; // 默认20字符
        
        long memorySize = calculatePromotionQueueMemorySize(size, avgKeyLength);
        
        return new RecorderStatistics.ComponentStatistics(size, memorySize, avgKeyLength);
    }

    /**
     * 计算 RecentQps 的统计信息
     * 
     * @return RecentQps 统计信息
     */
    private RecorderStatistics.ComponentStatistics calculateRecentQpsStatistics() {
        int size = recentQpsTable.size();
        if (size == 0) {
            return new RecorderStatistics.ComponentStatistics(0, 0L, 0);
        }
        
        // 计算平均key长度（采样前100个）
        int sampleSize = Math.min(size, 100);
        int totalKeyLength = 0;
        int count = 0;
        for (String key : recentQpsTable.keySet()) {
            if (count >= sampleSize) {
                break;
            }
            totalKeyLength += key != null ? key.length() : 0;
            count++;
        }
        int avgKeyLength = count > 0 ? totalKeyLength / count : 20; // 默认20字符
        
        long memorySize = calculateRecentQpsTableMemorySize(size, avgKeyLength);
        
        return new RecorderStatistics.ComponentStatistics(size, memorySize, avgKeyLength);
    }
    
    /**
     * 计算 PromotionQueue 的内存大小（内部方法，复用计算逻辑）
     * PromotionQueue 存储的是 ConcurrentHashMap<String, AccessInfo>
     * 
     * @param size 队列大小
     * @param avgKeyLength 平均key长度
     * @return 估算的内存大小（字节）
     */
    private long calculatePromotionQueueMemorySize(int size, int avgKeyLength) {
        if (size == 0) {
            return 0L;
        }
        
        // 每个key的内存估算：
        // - String对象：16(对象头) + 8(引用) + 4(hash) + 4(对齐) + 16(char[]对象头) + 4(长度) + 4(对齐) + 字符数*2
        long stringSize = 16 + 8 + 4 + 4 + 16 + 4 + 4 + (avgKeyLength * 2L);
        stringSize = (stringSize + 7) & ~7; // 对齐到8字节
        
        // AccessInfo对象：约80字节
        long accessInfoSize = 80L;
        
        // LongAdder对象：约48字节
        long longAdderSize = 48L;
        
        // ConcurrentHashMap.Entry：约48字节
        long entrySize = 48L;
        
        // 每个条目的总大小
        long perEntrySize = stringSize + accessInfoSize + longAdderSize + entrySize;
        
        // ConcurrentHashMap基础开销 + 数组开销
        long baseSize = 48L + (size * 2 * 8L); // 假设负载因子0.5
        
        return baseSize + (size * perEntrySize);
    }

    /**
     * 计算 RecentQps 的内存大小（内部方法）
     * RecentQps 存储的是 ConcurrentHashMap<String, Integer>
     * 
     * @param size 表大小
     * @param avgKeyLength 平均key长度
     * @return 估算的内存大小（字节）
     */
    private long calculateRecentQpsTableMemorySize(int size, int avgKeyLength) {
        if (size == 0) {
            return 0L;
        }
        
        // 每个key的内存估算：
        // - String对象：16(对象头) + 8(引用) + 4(hash) + 4(对齐) + 16(char[]对象头) + 4(长度) + 4(对齐) + 字符数*2
        long stringSize = 16 + 8 + 4 + 4 + 16 + 4 + 4 + (avgKeyLength * 2L);
        stringSize = (stringSize + 7) & ~7; // 对齐到8字节
        
        // Integer对象：16(对象头) + 4(value) + 4(对齐) = 24字节
        long integerSize = 24L;
        
        // ConcurrentHashMap.Entry：约48字节
        long entrySize = 48L;
        
        // 每个条目的总大小
        long perEntrySize = stringSize + integerSize + entrySize;
        
        // ConcurrentHashMap基础开销 + 数组开销
        long baseSize = 48L + (size * 2 * 8L); // 假设负载因子0.5
        
        return baseSize + (size * perEntrySize);
    }


    /**
     * 清理访问统计表（recentQpsTable）
     * 在降级任务执行完后调用，清理访问统计表
     * 外部调用此方法时，会异步执行清理任务，不阻塞调用线程
     */
    @Override
    public void cleanupRecentQpsTable() {
        // 如果已有清理任务在执行，跳过本次触发（避免并发执行）
        if (!cleanupInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            cleanupRecentQpsTableInternal();
        } finally {
            // 清理完成，重置标志
            cleanupInProgress.set(false);
        }
    }

    /**
     * 清理访问统计表（promotionQueue）的实际执行逻辑
     * 包含两种触发机制：
     * 1. 时间触发：清理超过指定时间未访问的非活跃key
     * 2. 容量触发：当容量超限时，按QPS排序，移除QPS最低的key
     * 
     * 保留热Key和温Key，优先清理冷Key
     * 清理策略：保留80%的容量，清理20%
     */
    private void cleanupPromotionQueueInternal() {
        long startTime = System.currentTimeMillis();
        int initialSize = promotionQueue.size();
        try {
            // 第一部分：时间触发 - 清理过期key
            int expiredCount = cleanupPromotionQueueByExpiredKeys();
            // 第二部分：容量触发 - 清理低QPS key
            int currentSize = promotionQueue.size();
            int lowQpsCount = 0;
            if (currentSize >= maxCapacity) {
                lowQpsCount = cleanupPromotionQueueByLowQpsKeys(currentSize);
            }
            // 记录清理结果
            if (expiredCount > 0 || lowQpsCount > 0) {
                long cost = System.currentTimeMillis() - startTime;
                log.info("清理promotionQueue完成, 过期清理: {}, 低QPS清理: {}, 清理前容量: {}, 清理后容量: {}, 耗时: {}ms",
                        expiredCount, lowQpsCount, initialSize, promotionQueue.size(), cost);
            }
        } catch (Exception e) {
            log.error("清理访问统计表失败", e);
        }
    }

    /**
     * 时间触发：清理超过指定时间未访问的非活跃key
     * 
     * @return 清理的key数量
     */
    private int cleanupPromotionQueueByExpiredKeys() {
        long currentTime = System.currentTimeMillis();
        long expireThreshold = currentTime - inactiveExpireTimeMs;
        // 收集需要过期的key
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, AccessInfo> entry : promotionQueue.entrySet()) {
            AccessInfo info = entry.getValue();
            // 如果最后访问时间早于过期阈值，标记为需要移除
            if (info.getLastAccessTime() < expireThreshold) {
                expiredKeys.add(entry.getKey());
            }
        }
        // 移除过期的key
        int removedCount = 0;
        for (String key : expiredKeys) {
            if (promotionQueue.remove(key) != null) {
                removedCount++;
            }
        }
        return removedCount;
    }

    /**
     * 清理待晋升队列（promotionQueue）
     * 在晋升任务执行完后调用，清理待晋升队列
     * 清理策略：保留80%的容量，清理20%
     * 用于清理promotionQueue中超过容量的key，避免内存泄漏
     * 
     * 注意：此方法线程安全，支持并发调用
     */
    public void cleanupPromotionQueue() {
        // 如果已有清理任务在执行，跳过本次触发（避免并发执行）
        if (!promotionQueueCleanupInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            cleanupPromotionQueueInternal();
        } finally {
            // 清理完成，重置标志
            promotionQueueCleanupInProgress.set(false);
        }
    }

    /**
     * 清理待晋升队列（recentQpsTable）的实际执行逻辑
     * 使用快照方式收集key，然后批量删除（线程安全）
     * 清理到目标容量（保留80%），避免迭代期间的并发修改问题
     */
    private void cleanupRecentQpsTableInternal() {
        long startTime = System.currentTimeMillis();
        int recentQpsTableSize = recentQpsTable.size();
        int targetSize = (int) (maxPromotionQueueCapacity * CAPACITY_RETAIN_RATIO);
        // 如果待晋升队列大小未超过目标容量，无需清理
        if (recentQpsTableSize <= targetSize) {
            return;
        }
        int toRemove = recentQpsTableSize - targetSize;
        // 使用快照方式收集要删除的key，避免迭代期间的并发修改问题
        List<String> keysToRemove = new ArrayList<>(toRemove);
        int count = 0;
        for (String key : recentQpsTable.keySet()) {
            if (count >= toRemove) {
                break;
            }
            keysToRemove.add(key);
            count++;
        }
        // 批量删除：使用remove()方法，即使key在删除期间被重新添加，也会被删除
        int removed = 0;
        for (String key : keysToRemove) {
            // remove()是原子操作，线程安全
            // 即使recordAccess在删除期间执行compute，也不会影响删除操作
            if (recentQpsTable.remove(key) != null) {
                removed++;
            }
        }
        // 记录清理结果
        if (removed > 0) {
            long cost = System.currentTimeMillis() - startTime;
            log.info("清理recentQpsTable完成, 清理前容量: {}, 清理后容量: {}, 清理数量: {}, 耗时: {}ms",
                    recentQpsTableSize, recentQpsTable.size(), removed, cost);
        }
    }

    /**
     * 容量触发：简化版清理低QPS key
     * 直接遍历收集低QPS key，排序后清理末尾
     * 
     * @param currentSize 当前容量
     * @return 清理的key数量
     */
    private int cleanupPromotionQueueByLowQpsKeys(int currentSize) {
        int toRemove = calculateKeysToRemove(currentSize);
        if (toRemove <= 0) {
            return 0;
        }
        double warmKeyThreshold = config.getDetection().getWarmKeyQpsThreshold();
        // 收集所有冷Key（QPS < warmKeyThreshold）及其QPS
        List<Map.Entry<String, Double>> coldKeys = new ArrayList<>();
        for (Map.Entry<String, AccessInfo> entry : promotionQueue.entrySet()) {
            AccessInfo info = entry.getValue();
            info.updateQps();
            double qps = info.getQps();
            // 只收集冷Key
            if (qps < warmKeyThreshold) {
                coldKeys.add(new AbstractMap.SimpleEntry<>(entry.getKey(), qps));
            }
        }
        
        // 如果冷Key数量不足，按QPS排序所有key（但优先保留热Key和温Key）
        if (coldKeys.size() < toRemove) {
            // 收集所有key的QPS
            List<Map.Entry<String, Double>> allKeys = new ArrayList<>();
            for (Map.Entry<String, AccessInfo> entry : promotionQueue.entrySet()) {
                AccessInfo info = entry.getValue();
                info.updateQps();
                allKeys.add(new AbstractMap.SimpleEntry<>(entry.getKey(), info.getQps()));
            }
            // 按QPS升序排序
            allKeys.sort(Comparator.comparingDouble(Map.Entry::getValue));
            // 取前toRemove个（QPS最小的）
            coldKeys = allKeys.subList(0, Math.min(toRemove, allKeys.size()));
        } else {
            // 冷Key数量足够，按QPS升序排序
            coldKeys.sort(Comparator.comparingDouble(Map.Entry::getValue));
            // 只取前toRemove个
            coldKeys = coldKeys.subList(0, Math.min(toRemove, coldKeys.size()));
        }
        
        // 移除收集到的key
        int removedCount = 0;
        for (Map.Entry<String, Double> entry : coldKeys) {
            if (promotionQueue.remove(entry.getKey()) != null) {
                removedCount++;
            }
        }
        return removedCount;
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
     * 访问信息
     */
    public static class AccessInfo {
        private final String key;
        private final LongAdder accessCount;
        private volatile long windowStartTime;
        private volatile long lastAccessTime;
        private volatile double qps;
        private volatile double previousWindowQps; // 上一个窗口的QPS，用于延迟重置时的衰减计算
        private final long windowSize;

        public AccessInfo(String key, long windowSize) {
            this.key = key;
            this.windowSize = windowSize;
            this.accessCount = new LongAdder();
            long currentTime = System.currentTimeMillis();
            this.windowStartTime = currentTime;
            this.lastAccessTime = currentTime;
            this.qps = 0.0;
            this.previousWindowQps = 0.0;
        }

        public void recordAccess() {
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - windowStartTime;
            long windowSizeMs = windowSize * 1000L;

            // 如果超过窗口大小，重置窗口
            // 注意：recordAccess需要及时重置窗口以开始新的计数，但updateQps会使用延迟重置策略避免抖动
            if (elapsed >= windowSizeMs) {
                resetWindow(currentTime);
            }

            accessCount.increment();
            lastAccessTime = currentTime;
        }

        private void resetWindow(long currentTime) {
            // 保存当前QPS作为上一个窗口的QPS，用于延迟重置时的衰减计算
            previousWindowQps = qps;
            windowStartTime = currentTime;
            accessCount.reset();
            qps = 0.0;
        }

        public void updateQps() {
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - windowStartTime;
            long windowSizeMs = windowSize * 1000L;

            // 窗口未过期，正常计算QPS
            if (elapsed < windowSizeMs) {
                if (elapsed > 0) {
                    long count = accessCount.sum();
                    // 平滑QPS计算，避免窗口刚重置后短时间内大量访问导致的QPS虚高问题
                    // 例如：
                    // - 窗口10秒，刚过1秒访问100次：QPS = 100/10 = 10（平滑后的QPS，避免虚高）
                    // - 窗口10秒，已过10秒访问100次：QPS = 100/10 = 10（平均QPS）
                    // 这种方式虽然会在窗口未满时略微低估QPS，但可以避免突发访问导致的误判
                    qps = count / (double) windowSize;
                } else {
                    qps = 0.0;
                }
                return;
            }

            // 窗口已过期，使用延迟重置策略避免QPS突然变为0导致的抖动
            // 策略：窗口过期后，在0.5倍窗口时间内使用衰减策略，超过1.5倍窗口时间才真正重置
            long expiredTime = elapsed - windowSizeMs;
            long delayResetThreshold = (long) (windowSizeMs * 0.5); // 延迟重置阈值：窗口大小的50%
            
            // 如果超过1.5倍窗口时间，才真正重置窗口
            if (expiredTime >= delayResetThreshold) {
                resetWindow(currentTime);
                qps = 0.0;
                return;
            }
            
            // 窗口刚过期（0到0.5倍窗口时间内），使用衰减策略
            // 使用保存的上一个窗口的QPS，逐渐衰减，避免突然降级
            // 注意：recordAccess可能已经重置了窗口，所以accessCount是新窗口的计数
            // 我们使用previousWindowQps（在resetWindow时保存）进行衰减计算
            
            // 衰减因子：过期时间越长，衰减越多
            // expiredTime在0到delayResetThreshold之间，衰减因子从1.0降到0.0
            double decayFactor = 1.0 - (expiredTime / (double) delayResetThreshold);
            qps = previousWindowQps * Math.max(0.0, decayFactor);
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
