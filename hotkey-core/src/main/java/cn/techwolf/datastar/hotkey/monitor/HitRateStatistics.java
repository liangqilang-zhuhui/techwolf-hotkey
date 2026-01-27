package cn.techwolf.datastar.hotkey.monitor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 热Key命中率统计器实现
 * 职责：使用原子类和时间窗口统计热Key缓存命中率，保证线程安全
 * 
 * 性能优化：
 * 1. 使用 CAS 操作替代同步块，避免高并发下的阻塞
 * 2. 窗口重置采用无锁算法，只有真正需要重置的线程才执行重置
 * 
 * 内存优化：
 * 1. 限制 windowKeys 最大大小，防止OOM
 * 2. 定期清理过期key，防止内存泄漏
 * 3. 超过限制时拒绝新key，保护系统稳定性
 *
 * @author techwolf
 * @date 2024
 */
public class HitRateStatistics implements IHitRateStatistics {
    /**
     * 统计窗口大小（秒），用于计算QPS
     */
    private static final int WINDOW_SIZE_SECONDS = 10;

    /**
     * 清理间隔（毫秒），每5秒清理一次过期key
     */
    private static final long CLEANUP_INTERVAL_MS = 5000;

    /**
     * 最大key数量限制，防止OOM
     * 如果超过此限制，会触发清理或拒绝新key
     */
    private static final int MAX_WINDOW_KEYS = 100000;

    /**
     * wrapGet总调用次数
     */
    private final LongAdder totalWrapGetCount = new LongAdder();

    /**
     * 热Key访问总次数
     */
    private final LongAdder hotKeyAccessCount = new LongAdder();

    /**
     * 热Key缓存命中次数
     */
    private final LongAdder hotKeyHitCount = new LongAdder();

    /**
     * 热Key缓存未命中次数
     */
    private final LongAdder hotKeyMissCount = new LongAdder();

    /**
     * 时间窗口开始时间（毫秒）
     * 使用 AtomicLong 支持 CAS 操作
     */
    private final AtomicLong windowStartTime = new AtomicLong(System.currentTimeMillis());

    /**
     * 窗口内的访问次数（用于计算QPS）
     */
    private final LongAdder windowAccessCount = new LongAdder();

    /**
     * 窗口内的热Key访问次数（用于计算热Key访问QPS）
     */
    private final LongAdder windowHotKeyAccessCount = new LongAdder();

    /**
     * 窗口内的热Key命中次数（用于计算热Key命中QPS）
     */
    private final LongAdder windowHotKeyHitCount = new LongAdder();

    /**
     * 窗口内的热Key未命中次数（用于计算热Key未命中QPS）
     */
    private final LongAdder windowHotKeyMissCount = new LongAdder();

    /**
     * 窗口内访问的不同key集合（用于计算每秒key数量）
     * 内存优化：限制最大大小，防止OOM
     */
    private final ConcurrentHashMap<String, Long> windowKeys = new ConcurrentHashMap<>();

    /**
     * 窗口重置标志（用于 CAS 操作，确保只有一个线程执行重置）
     * true 表示正在重置，false 表示未在重置
     */
    private final AtomicBoolean resettingWindow = new AtomicBoolean(false);

    /**
     * 上次清理时间（毫秒），用于定期清理过期key
     * 使用 AtomicLong 支持 CAS 操作，确保只有一个线程执行清理
     */
    private final AtomicLong lastCleanupTime = new AtomicLong(System.currentTimeMillis());

    @Override
    public void recordWrapGet(String key) {
        if (key == null) {
            return;
        }

        // 记录总调用次数
        totalWrapGetCount.increment();

        // 更新窗口统计
        updateWindow(key);
    }

    @Override
    public void recordHotKeyAccess() {
        hotKeyAccessCount.increment();
        // 同时更新窗口统计，用于计算热Key访问QPS
        windowHotKeyAccessCount.increment();
    }

    @Override
    public void recordHotKeyHit() {
        hotKeyHitCount.increment();
        // 同时更新窗口统计，用于计算热Key命中QPS
        windowHotKeyHitCount.increment();
    }

    @Override
    public void recordHotKeyMiss() {
        hotKeyMissCount.increment();
        // 同时更新窗口统计，用于计算热Key未命中QPS
        windowHotKeyMissCount.increment();
    }

    /**
     * 更新窗口统计
     * 
     * 性能优化：使用 CAS 操作替代同步块
     * 内存优化：定期清理过期key，限制Map大小，防止OOM
     * 1. 大部分线程快速通过，不阻塞
     * 2. 只有真正需要重置的线程才执行重置操作
     * 3. 其他线程在重置期间可以继续执行，不会被阻塞
     * 4. 定期清理过期key，防止内存泄漏
     * 5. 超过限制时拒绝新key，保护系统稳定性
     *
     * @param key 访问的key
     */
    private void updateWindow(String key) {
        long currentTime = System.currentTimeMillis();
        long windowStart = windowStartTime.get();
        long elapsed = currentTime - windowStart;

        // 如果超过窗口大小，尝试重置窗口
        if (elapsed >= WINDOW_SIZE_SECONDS * 1000) {
            // 使用 CAS 操作，只有第一个线程能成功设置重置标志
            if (resettingWindow.compareAndSet(false, true)) {
                try {
                    // 双重检查：再次检查窗口是否过期（可能已被其他线程重置）
                    windowStart = windowStartTime.get();
                    elapsed = currentTime - windowStart;
                    
                    if (elapsed >= WINDOW_SIZE_SECONDS * 1000) {
                        // 执行窗口重置
                        windowStartTime.set(currentTime);
                        windowAccessCount.reset();
                        windowHotKeyAccessCount.reset();
                        windowHotKeyHitCount.reset();
                        windowHotKeyMissCount.reset();
                        windowKeys.clear();
                        lastCleanupTime.set(currentTime);
                    }
                } finally {
                    // 释放重置标志，允许其他线程在下次窗口过期时重置
                    resettingWindow.set(false);
                }
            }
            // 如果 CAS 失败，说明其他线程正在重置，当前线程直接继续执行
            // 不需要等待，避免阻塞
        }

        // 定期清理过期key（防止内存泄漏）
        // 每5秒清理一次，避免频繁清理影响性能
        long timeSinceLastCleanup = currentTime - lastCleanupTime.get();
        if (timeSinceLastCleanup >= CLEANUP_INTERVAL_MS) {
            cleanupExpiredKeys(currentTime);
        }

        // 记录窗口内的访问（无锁操作，高性能）
        windowAccessCount.increment();
        
        // 记录窗口内访问的key（使用时间戳标记，用于后续清理）
        // 如果Map大小超过限制，先清理过期key再添加
        int currentSize = windowKeys.size();
        if (currentSize >= MAX_WINDOW_KEYS) {
            // 触发清理，释放空间
            cleanupExpiredKeys(currentTime);
            
            // 如果清理后仍然超过限制，拒绝新key（保护机制）
            currentSize = windowKeys.size();
            if (currentSize >= MAX_WINDOW_KEYS) {
                // 记录警告日志，但不影响主流程
                // 注意：这里不记录日志，避免日志过多影响性能
                return;
            }
        }
        
        windowKeys.put(key, currentTime);
    }

    /**
     * 清理过期的key
     * 
     * 使用 CAS 操作确保只有一个线程执行清理，避免重复清理
     * 
     * @param currentTime 当前时间（毫秒）
     */
    private void cleanupExpiredKeys(long currentTime) {
        long expectedCleanupTime = lastCleanupTime.get();
        long timeSinceLastCleanup = currentTime - expectedCleanupTime;
        
        // 如果距离上次清理时间不足清理间隔，跳过清理
        if (timeSinceLastCleanup < CLEANUP_INTERVAL_MS) {
            return;
        }
        
        // 使用 CAS 操作，只有第一个线程能成功更新清理时间
        if (lastCleanupTime.compareAndSet(expectedCleanupTime, currentTime)) {
            try {
                // CAS 成功，执行清理
                long expireThreshold = currentTime - WINDOW_SIZE_SECONDS * 1000;
                
                // 清理过期key
                windowKeys.entrySet().removeIf(entry -> entry.getValue() < expireThreshold);
            } catch (Exception e) {
                // 清理失败，恢复清理时间，允许下次重试
                lastCleanupTime.set(expectedCleanupTime);
            }
        }
        // 如果 CAS 失败，说明其他线程正在清理，当前线程直接继续执行
        // 不需要等待，避免阻塞
    }

    @Override
    public long getTotalWrapGetCount() {
        return totalWrapGetCount.sum();
    }

    @Override
    public double getWrapGetQps() {
        long currentTime = System.currentTimeMillis();
        long windowStart = windowStartTime.get();
        long elapsed = currentTime - windowStart;

        if (elapsed <= 0) {
            return 0.0;
        }

        // 清理过期key（超过窗口时间的key）
        long expireThreshold = currentTime - WINDOW_SIZE_SECONDS * 1000;
        windowKeys.entrySet().removeIf(entry -> entry.getValue() < expireThreshold);

        // 计算QPS
        long count = windowAccessCount.sum();
        return (count * 1000.0) / elapsed;
    }

    @Override
    public double getKeysPerSecond() {
        long currentTime = System.currentTimeMillis();
        long windowStart = windowStartTime.get();
        long elapsed = currentTime - windowStart;

        if (elapsed <= 0) {
            return 0.0;
        }

        // 清理过期key
        long expireThreshold = currentTime - WINDOW_SIZE_SECONDS * 1000;
        windowKeys.entrySet().removeIf(entry -> entry.getValue() < expireThreshold);

        // 计算每秒key数量
        int keyCount = windowKeys.size();
        return (keyCount * 1000.0) / elapsed;
    }

    @Override
    public long getHotKeyAccessCount() {
        return hotKeyAccessCount.sum();
    }

    @Override
    public long getHotKeyHitCount() {
        return hotKeyHitCount.sum();
    }

    @Override
    public long getHotKeyMissCount() {
        return hotKeyMissCount.sum();
    }

    @Override
    public double getHotKeyHitRate() {
        long hits = getHotKeyHitCount();
        long misses = getHotKeyMissCount();
        long total = hits + misses;
        if (total == 0) {
            return 0.0;
        }
        return (double) hits / total;
    }

    @Override
    public double getHotKeyTrafficRatio() {
        long total = getTotalWrapGetCount();
        if (total == 0) {
            return 0.0;
        }
        long hotKeyAccess = getHotKeyAccessCount();
        return (double) hotKeyAccess / total;
    }

    @Override
    public double getHotKeyAccessQps() {
        long currentTime = System.currentTimeMillis();
        long windowStart = windowStartTime.get();
        long elapsed = currentTime - windowStart;

        if (elapsed <= 0) {
            return 0.0;
        }

        // 计算热Key访问QPS
        long count = windowHotKeyAccessCount.sum();
        return (count * 1000.0) / elapsed;
    }

    @Override
    public double getHotKeyHitQps() {
        long currentTime = System.currentTimeMillis();
        long windowStart = windowStartTime.get();
        long elapsed = currentTime - windowStart;

        if (elapsed <= 0) {
            return 0.0;
        }

        // 计算热Key命中QPS
        long count = windowHotKeyHitCount.sum();
        return (count * 1000.0) / elapsed;
    }

    @Override
    public double getHotKeyMissQps() {
        long currentTime = System.currentTimeMillis();
        long windowStart = windowStartTime.get();
        long elapsed = currentTime - windowStart;

        if (elapsed <= 0) {
            return 0.0;
        }

        // 计算热Key未命中QPS
        long count = windowHotKeyMissCount.sum();
        return (count * 1000.0) / elapsed;
    }

    @Override
    public void reset() {
        totalWrapGetCount.reset();
        hotKeyAccessCount.reset();
        hotKeyHitCount.reset();
        hotKeyMissCount.reset();
        windowAccessCount.reset();
        windowHotKeyAccessCount.reset();
        windowHotKeyHitCount.reset();
        windowHotKeyMissCount.reset();
        windowKeys.clear();
        long currentTime = System.currentTimeMillis();
        windowStartTime.set(currentTime);
        lastCleanupTime.set(currentTime);
    }
}
