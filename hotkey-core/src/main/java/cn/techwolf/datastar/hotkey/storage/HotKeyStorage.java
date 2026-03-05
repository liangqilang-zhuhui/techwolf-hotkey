package cn.techwolf.datastar.hotkey.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 热Key数据存储实现（模块二）
 * 职责：
 * 1. 存储热key的value，采用local cache
 * 2. 管理数据获取回调函数注册表
 * 3. 定时刷新热Key数据，保证数据新鲜度
 * 4. 过期时间可配置（默认60分钟）
 * 5. 支持null值缓存（使用特殊标记值）
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class HotKeyStorage implements IHotKeyStorage {

    /**
     * null值的特殊标记
     * 使用特殊前缀，确保不会与真实数据冲突
     * Caffeine cache不允许null值，因此使用此标记值表示null
     */
    private static final String NULL_VALUE_MARKER = "__HOTKEY_NULL__";

    private final Cache<String, String> cache;
    private final ConcurrentHashMap<String, Function<String, String>> registryUpdateKeys;
    private ScheduledExecutorService scheduler;

    public HotKeyStorage(HotKeyConfig config) {
        HotKeyConfig.Storage storageConfig = config.getStorage();
        this.cache = Caffeine.newBuilder()
                .maximumSize(storageConfig.getMaximumSize())
                .expireAfterWrite(storageConfig.getExpireAfterWrite(), TimeUnit.MINUTES)
                .recordStats() // 记录统计信息
                .build();
        this.registryUpdateKeys = new ConcurrentHashMap<>();

        scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "HotKey-Storage-Updater-Scheduler");
            t.setDaemon(true);
            return t;
        });

        long refreshInterval = config.getRefresh().getInterval();
        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        refreshAllKeys();
                    } catch (Exception e) {
                        log.error("定时刷新所有热Key异常", e);
                    }
                },
                refreshInterval,
                refreshInterval,
                TimeUnit.MILLISECONDS
        );
        log.info("热Key数据存储初始化完成, 最大容量: {}, 过期时间: {}分钟",
                storageConfig.getMaximumSize(),
                storageConfig.getExpireAfterWrite());
    }

    @Override
    public String get(String key) {
        if (key == null) {
            return null;
        }
        try {
            String value = cache.getIfPresent(key);
            if (value == null) {
                // 缓存未命中
                return null;
            }
            // 检查是否为null标记值
            if (NULL_VALUE_MARKER.equals(value)) {
                // 这是缓存的null值，返回null
                return null;
            }
            // 返回实际值
            return value;
        } catch (Exception e) {
            log.debug("从数据存储获取数据失败, key: {}", key, e);
            return null;
        }
    }

    @Override
    public void put(String key, String value, Function<String, String> dataGetter) {
        if (key == null) {
            return;
        }
        try {
            // 更新缓存值（处理null值转换和存储）
            updateCacheValue(key, value);
            
            // 注册回调函数
            Function<String, String> previous = registryUpdateKeys.putIfAbsent(key, dataGetter);
            if (previous == null) {
                // 成功注册（首次注册）
                log.debug("注册数据获取回调函数成功, key: {}", key);
            }
        } catch (Exception e) {
            log.debug("写入数据存储失败, key: {}", key, e);
        }
    }

    /**
     * 更新缓存值（内部方法）
     * 处理null值转换、存储到cache、记录日志
     *
     * @param key Redis key
     * @param value 要缓存的值（可能为null）
     */
    private void updateCacheValue(String key, String value) {
        if (key == null) {
            return;
        }
        try {
            // null值处理：存储标记值
            String cacheValue = (value == null) ? NULL_VALUE_MARKER : value;
            cache.put(key, cacheValue);
            
            if (value == null) {
                log.debug("更新缓存为null值标记, key: {}", key);
            }
        } catch (Exception e) {
            log.warn("更新缓存失败, key: {}", key, e);
        }
    }

    private void refreshAllKeys() {
        // 创建快照，避免并发修改导致的遍历问题
        Set<String> allKeys = new HashSet<>(registryUpdateKeys.keySet());
        for (String key : allKeys) {
            // 在刷新前再次检查key是否还存在（可能已被降级移除）
            if (!registryUpdateKeys.containsKey(key)) {
                continue;
            }
            try {
                refreshSingleKey(key);
            } catch (Exception e) {
                log.warn("自动刷新热Key异常, key: {}", key, e);
            }
        }
    }

    /**
     * 刷新单个key
     *
     * @param key Redis key
     */
    private void refreshSingleKey(String key) {
        // 参数校验
        if (key == null) {
            log.debug("刷新热Key失败, key为null");
            return;
        }
        
        // 获取数据获取回调函数（如果key已被降级移除，这里会返回null）
        Function<String, String> dataGetter = registryUpdateKeys.get(key);
        if (dataGetter == null) {
            // key已被移除，无需刷新
            log.debug("刷新热Key失败, key已被移除: {}", key);
            return;
        }
        
        // 调用数据获取回调函数获取最新值
        String newValue;
        try {
            newValue = dataGetter.apply(key);
        } catch (Exception e) {
            log.warn("调用数据获取回调函数失败, key: {}", key, e);
            return;
        }
        
        // 更新缓存值（处理null值转换和存储）
        updateCacheValue(key, newValue);
    }

    public void remove(String key) {
        try {
            cache.invalidate(key);
            registryUpdateKeys.remove(key);
        } catch (Exception e) {
            log.debug("从数据存储删除数据失败, key: {}", key, e);
        }
    }

    /**
     * 只保留指定key集合，删除其他所有key
     * 会同时清理缓存（cache）和注册表（registryUpdateKeys）中的key
     *
     * @param keysToRetain 需要保留的key集合
     */
    @Override
    public void retainAll(Set<String> keysToRetain) {
        if (keysToRetain == null) {
            return;
        }
        try {
            // 获取所有已注册的key
            Set<String> allKeys = Collections.unmodifiableSet(registryUpdateKeys.keySet());
            // 找出需要删除的key（不在保留集合中的key）
            for (String key : allKeys) {
                if (!keysToRetain.contains(key)) {
                    remove(key);
                }
            }
        } catch (Exception e) {
            log.debug("只保留指定key集合失败", e);
        }
    }

    @Override
    public long size() {
        try {
            return cache.estimatedSize();
        } catch (Exception e) {
            log.debug("获取数据存储大小失败", e);
            return 0;
        }
    }

    /**
     * 获取热Key的值（带命中状态）
     * 一次查询返回命中状态和值，避免多次查询cache
     *
     * @param key Redis key
     * @return 缓存获取结果，包含命中状态和值
     */
    @Override
    public CacheGetResult getWithHit(String key) {
        if (key == null) {
            return new CacheGetResult(false, null);
        }
        try {
            String cacheValue = cache.getIfPresent(key);
            if (cacheValue == null) {
                // 缓存未命中
                return new CacheGetResult(false, null);
            }
            // 检查是否为null标记值
            if (NULL_VALUE_MARKER.equals(cacheValue)) {
                // 这是缓存的null值，命中但值为null
                return new CacheGetResult(true, null);
            }
            // 返回实际值，命中且值非null
            return new CacheGetResult(true, cacheValue);
        } catch (Exception e) {
            log.debug("从数据存储获取数据失败, key: {}", key, e);
            return new CacheGetResult(false, null);
        }
    }

    /**
     * 关闭存储，释放资源
     * 停止定时刷新任务，释放线程池资源
     */
    public void shutdown() {
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
            log.info("热Key数据存储已关闭");
        }
    }
}
