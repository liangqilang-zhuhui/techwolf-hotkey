package cn.techwolf.datastar.hotkey.updater;

import lombok.extern.slf4j.Slf4j;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.storage.IHotKeyStorage;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 缓存数据更新器实现（模块六）
 * 职责：存储和管理每个Redis key对应的数据获取回调函数，并负责自动刷新缓存数据
 *
 * 设计特点：
 * - 高内聚：所有数据刷新相关的功能都集中在此模块（存储回调函数 + 定时刷新）
 * - 低耦合：通过接口依赖其他模块
 * - 责任清晰：负责缓存数据的更新（存储回调函数和自动刷新）
 * - 线程安全：使用ConcurrentHashMap，支持高并发读写
 * - 高性能：所有操作都是O(1)时间复杂度
 * - 异常隔离：单个key刷新失败不影响其他key
 * - 自动清理：刷新失败超过阈值后自动移除
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class CacheDataUpdater implements ICacheDataUpdater {
    /**
     * 存储数据获取回调函数的Map，key为Redis key，value为数据获取回调函数
     * 使用ConcurrentHashMap保证线程安全
     */
    private final ConcurrentHashMap<String, Function<String, String>> registry;

    /**
     * 数据存储（用于更新缓存）
     */
    private final IHotKeyStorage hotKeyStorage;

    /**
     * 配置参数
     */
    private final HotKeyConfig config;

    /**
     * 是否启用自动刷新
     */
    private final boolean enabled;

    /**
     * 定时任务执行器
     */
    private ScheduledExecutorService scheduler;

    /**
     * 刷新失败计数器，用于记录每个key的连续失败次数
     * key为Redis key，value为失败次数
     */
    private final ConcurrentHashMap<String, Integer> refreshFailureCount;

    /**
     * 是否运行中
     */
    private volatile boolean running = false;

    /**
     * 构造函数，初始化注册表和刷新服务
     *
     * @param hotKeyStorage 数据存储
     * @param config 配置参数
     */
    public CacheDataUpdater(IHotKeyStorage hotKeyStorage, HotKeyConfig config) {
        this.registry = new ConcurrentHashMap<>();
        this.hotKeyStorage = hotKeyStorage;
        this.config = config;
        this.enabled = config != null && config.getRefresh() != null && config.getRefresh().isEnabled();
        this.refreshFailureCount = new ConcurrentHashMap<>();

        if (enabled) {
            log.info("缓存数据更新器初始化完成，自动刷新已启用");
        } else {
            log.info("缓存数据更新器初始化完成，自动刷新未启用");
        }
    }

    /**
     * 注册数据获取回调函数
     * 如果key或dataGetter为null，则不进行注册
     *
     * @param key Redis key
     * @param dataGetter 数据获取回调函数（Function，接收key参数，返回value）
     */
    @Override
    public void register(String key, Function<String, String> dataGetter) {
        if (key == null) {
            log.debug("注册数据获取回调函数失败，key为null");
            return;
        }
        if (dataGetter == null) {
            log.debug("注册数据获取回调函数失败，dataGetter为null, key: {}", key);
            return;
        }
        try {
            if(!registry.containsKey(key)) {
                registry.put(key, dataGetter);
                log.debug("注册数据获取回调函数成功, key: {}", key);
            }
        } catch (Exception e) {
            log.debug("注册数据获取回调函数失败, key: {}", key, e);
        }
    }

    /**
     * 获取指定key的数据获取回调函数
     *
     * @param key Redis key
     * @return 数据获取回调函数，如果未注册返回null
     */
    @Override
    public Function<String, String> get(String key) {
        if (key == null) {
            return null;
        }
        try {
            return registry.get(key);
        } catch (Exception e) {
            log.debug("获取数据获取回调函数失败, key: {}", key, e);
            return null;
        }
    }

    /**
     * 移除指定key的数据获取回调函数
     *
     * @param key Redis key
     */
    @Override
    public void remove(String key) {
        if (key == null) {
            return;
        }
        try {
            Function<String, String> removed = registry.remove(key);
            if (removed != null) {
                log.debug("移除数据获取回调函数成功, key: {}", key);
            } else {
                log.debug("移除数据获取回调函数失败，key未注册, key: {}", key);
            }
        } catch (Exception e) {
            log.debug("移除数据获取回调函数失败, key: {}", key, e);
        }
    }

    /**
     * 获取所有已注册的key
     *
     * @return 所有已注册的key集合（不可变集合）
     */
    @Override
    public Set<String> getAllKeys() {
        try {
            return Collections.unmodifiableSet(registry.keySet());
        } catch (Exception e) {
            log.debug("获取所有已注册的key失败", e);
            return Collections.emptySet();
        }
    }

    /**
     * 清空所有注册的数据获取回调函数
     */
    @Override
    public void clear() {
        try {
            int size = registry.size();
            registry.clear();
            log.debug("清空缓存数据更新器成功, 清空数量: {}", size);
        } catch (Exception e) {
            log.debug("清空缓存数据更新器失败", e);
        }
    }

    /**
     * 获取注册表大小
     *
     * @return 注册的key数量
     */
    @Override
    public int size() {
        try {
            return registry.size();
        } catch (Exception e) {
            log.debug("获取注册表大小失败", e);
            return 0;
        }
    }

    /**
     * 检查key是否已注册
     *
     * @param key Redis key
     * @return 如果已注册返回true，否则返回false
     */
    @Override
    public boolean contains(String key) {
        if (key == null) {
            return false;
        }
        try {
            return registry.containsKey(key);
        } catch (Exception e) {
            log.debug("检查key是否已注册失败, key: {}", key, e);
            return false;
        }
    }

    @Override
    public void start() {
        if (!enabled) {
            log.info("缓存数据更新器自动刷新未启用，不启动");
            return;
        }

        if (running) {
            log.warn("缓存数据更新器自动刷新服务已在运行中");
            return;
        }

        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "HotKey-CacheDataUpdater-Scheduler");
            t.setDaemon(true);
            return t;
        });

        long refreshInterval = config.getRefresh().getInterval();
        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        refreshHotKeys();
                    } catch (Exception e) {
                        log.error("缓存数据自动刷新任务执行失败", e);
                    }
                },
                refreshInterval,
                refreshInterval,
                TimeUnit.MILLISECONDS
        );

        running = true;
        log.info("缓存数据更新器自动刷新服务已启动，刷新间隔: {}ms", refreshInterval);
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        running = false;
        log.info("缓存数据更新器自动刷新服务已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 自动刷新热Key数据
     * 只刷新当前是热Key的数据，非热Key不需要刷新
     * 刷新失败时记录失败次数，超过阈值后移除该热key
     */
    private void refreshHotKeys() {
        if (!enabled) {
            return;
        }

        // 获取所有已注册的key
        Set<String> allKeys = getAllKeys();
        if (allKeys.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failureCount = 0;
        int removedCount = 0;

        // 遍历所有已注册的key，只刷新热Key
        // 注意：allKeys是注册表的快照，在遍历过程中如果key被降级移除，get(key)会返回null，我们跳过即可
        for (String key : allKeys) {
            try {
                // 获取数据获取回调函数（如果key已被降级移除，这里会返回null）
                Function<String, String> dataGetter = get(key);
                if (dataGetter == null) {
                    // key已被移除（可能被降级），跳过刷新
                    continue;
                }
                // 调用数据获取回调函数获取最新值
                String newValue = dataGetter.apply(key);
                if (newValue != null) {
                    // 刷新成功，更新缓存
                    if (hotKeyStorage != null) {
                        hotKeyStorage.put(key, newValue);
                    }
                    // 清除失败计数
                    refreshFailureCount.remove(key);
                    successCount++;
                    if (log.isDebugEnabled()) {
                        log.debug("自动刷新热Key成功, key: {}", key);
                    }
                } else {
                    // 值为null，可能是key不存在，记录失败
                    handleRefreshFailure(key);
                    failureCount++;
                }
            } catch (Exception e) {
                // 刷新异常，记录失败
                log.warn("自动刷新热Key异常, key: {}", key, e);
                handleRefreshFailure(key);
                failureCount++;
            }
        }

        // 移除失败次数超过阈值的key
        int maxFailureCount = config.getRefresh().getMaxFailureCount();
        // 先收集需要移除的key，避免在遍历时修改Map
        Set<String> keysToRemove = new HashSet<>();
        for (Map.Entry<String, Integer> entry : refreshFailureCount.entrySet()) {
            String key = entry.getKey();
            Integer count = entry.getValue();
            if (count != null && count >= maxFailureCount) {
                keysToRemove.add(key);
            }
        }
        // 统一移除
        for (String key : keysToRemove) {
            removeHotKey(key);
            removedCount++;
            Integer count = refreshFailureCount.remove(key);
            log.warn("热Key刷新失败次数超过阈值，已移除, key: {}, 失败次数: {}", key, count);
        }

        if (log.isDebugEnabled() && (successCount > 0 || failureCount > 0 || removedCount > 0)) {
            log.debug("缓存数据自动刷新完成, 成功: {}, 失败: {}, 移除: {}", successCount, failureCount, removedCount);
        }
    }

    /**
     * 处理刷新失败
     * 增加失败计数
     *
     * @param key Redis key
     */
    private void handleRefreshFailure(String key) {
        if (key == null) {
            return;
        }
        refreshFailureCount.compute(key, (k, v) -> v == null ? 1 : v + 1);
    }

    /**
     * 移除热Key
     * 从缓存、注册表、失败计数中移除
     *
     * @param key Redis key
     */
    private void removeHotKey(String key) {
        if (key == null) {
            return;
        }
        try {
            // 从缓存中移除
            if (hotKeyStorage != null) {
                hotKeyStorage.remove(key);
            }
            // 从注册表中移除
            remove(key);
            // 从失败计数中移除
            refreshFailureCount.remove(key);
            if (log.isDebugEnabled()) {
                log.debug("移除热Key完成, key: {}", key);
            }
        } catch (Exception e) {
            log.warn("移除热Key失败, key: {}", key, e);
        }
    }

    @Override
    public void cleanupDemotedKeys(Set<String> demotedKeys) {
        if (demotedKeys == null || demotedKeys.isEmpty()) {
            return;
        }
        for (String key : demotedKeys) {
            try {
                // 从注册表中移除（不再需要自动刷新）
                remove(key);
                // 从失败计数中移除
                refreshFailureCount.remove(key);
                if (log.isDebugEnabled()) {
                    log.debug("清理被降级key完成, key: {}", key);
                }
            } catch (Exception e) {
                log.warn("清理被降级key失败, key: {}", key, e);
            }
        }
    }
}
