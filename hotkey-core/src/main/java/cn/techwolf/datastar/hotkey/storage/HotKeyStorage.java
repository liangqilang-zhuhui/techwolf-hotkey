package cn.techwolf.datastar.hotkey.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;

import java.util.concurrent.TimeUnit;

/**
 * 热Key数据存储实现（模块二）
 * 职责：只存储热key的value，采用local cache，1分钟过期机制
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class HotKeyStorage implements IHotKeyStorage {
    /**
     * 本地缓存实例（Caffeine）
     */
    private final Cache<String, String> cache;

    public HotKeyStorage(HotKeyConfig config) {
        HotKeyConfig.Storage storageConfig = config.getStorage();
        
        // 创建Caffeine缓存，1分钟过期
        this.cache = Caffeine.newBuilder()
                .maximumSize(storageConfig.getMaximumSize())
                .expireAfterWrite(storageConfig.getExpireAfterWrite(), TimeUnit.SECONDS)
                .recordStats() // 记录统计信息
                .build();

        log.info("热Key数据存储初始化完成, 最大容量: {}, 过期时间: {}秒",
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
    public void put(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        try {
            cache.put(key, value);
        } catch (Exception e) {
            log.debug("写入数据存储失败, key: {}", key, e);
        }
    }

    @Override
    public void update(String key, String value) {
        // 更新和put操作相同
        put(key, value);
    }

    @Override
    public void remove(String key) {
        if (key == null) {
            return;
        }
        try {
            cache.invalidate(key);
        } catch (Exception e) {
            log.debug("从数据存储删除数据失败, key: {}", key, e);
        }
    }

    @Override
    public void clear() {
        try {
            cache.invalidateAll();
        } catch (Exception e) {
            log.debug("清空数据存储失败", e);
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
}
