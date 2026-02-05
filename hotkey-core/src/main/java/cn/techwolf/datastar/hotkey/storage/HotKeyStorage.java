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
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class HotKeyStorage implements IHotKeyStorage {

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
            return cache.getIfPresent(key);
        } catch (Exception e) {
            log.debug("从数据存储获取数据失败, key: {}", key, e);
            return null;
        }
    }

    @Override
    public void put(String key, String value,Function<String, String> dataGetter) {
        if (key == null || value == null) {
            return;
        }
        try {
            cache.put(key, value);
            Function<String, String> previous = registryUpdateKeys.putIfAbsent(key, dataGetter);
            if (previous == null) {
                // 成功注册（首次注册）
                log.debug("注册数据获取回调函数成功, key: {}", key);
            }
        } catch (Exception e) {
            log.debug("写入数据存储失败, key: {}", key, e);
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
        
        // 检查新值是否为null（Caffeine cache不允许null值）
        if (newValue == null) {
            log.debug("刷新热Key失败, 数据源返回null值, key: {}", key);
            return;
        }
        
        // 刷新成功，更新缓存
        try {
            cache.put(key, newValue);
        } catch (Exception e) {
            log.warn("更新缓存失败, key: {}", key, e);
        }
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
