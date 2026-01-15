package cn.techwolf.datastar.hotkey.core;

import lombok.extern.slf4j.Slf4j;

import cn.techwolf.datastar.hotkey.recorder.IAccessRecorder;
import cn.techwolf.datastar.hotkey.storage.IHotKeyStorage;
import cn.techwolf.datastar.hotkey.updater.ICacheDataUpdater;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 热Key管理器实现（模块一）
 * 职责：
 * 1. 记录访问日志
 * 2. 维护和管理热Key列表
 * 3. 判断是否热key
 * 4. 与Redis Client集成，处理get/set操作
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class HotKeyManager implements IHotKeyManager {
    /**
     * 当前热Key列表（线程安全）
     */
    private volatile Set<String> hotKeys;

    /**
     * 更新锁（避免并发更新）
     */
    private final Object updateLock = new Object();

    /**
     * 访问记录器
     */
    private final IAccessRecorder accessRecorder;

    /**
     * 数据存储
     */
    private final IHotKeyStorage hotKeyStorage;

    /**
     * 缓存数据更新器（用于清理被降级key的相关资源）
     */
    private final ICacheDataUpdater cacheDataUpdater;

    /**
     * 异步执行线程池（用于记录访问日志，避免阻塞主流程）
     * 使用有界队列和拒绝策略，防止OOM
     * 注意：使用静态线程池，所有实例共享，避免创建过多线程池
     */
    private static final ExecutorService asyncExecutor = new ThreadPoolExecutor(
            100, 1000, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10000),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "HotKey-Async-" + (++counter));
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 构造函数
     * 注意：hotKeySelector 参数保留以保持接口兼容性，但内部不再使用
     * 因为热Key列表现在由 Manager 自己维护
     *
     * @param accessRecorder 访问记录器
     * @param hotKeyStorage 数据存储
     * @param cacheDataUpdater 缓存数据更新器（可选，用于清理被降级key）
     */
    public HotKeyManager(IAccessRecorder accessRecorder,
                        IHotKeyStorage hotKeyStorage,
                        ICacheDataUpdater cacheDataUpdater) {
        this.accessRecorder = accessRecorder;
        this.hotKeyStorage = hotKeyStorage;
        this.cacheDataUpdater = cacheDataUpdater;
        this.hotKeys = Collections.emptySet();
    }

    @Override
    public void recordAccess(String key) {
        if (key == null) {
            return;
        }
        // 异步记录访问日志，使用自定义线程池避免OOM
        asyncExecutor.execute(() -> {
            try {
                accessRecorder.recordAccess(key);
            } catch (Exception e) {
                log.debug("记录访问日志失败, key: {}", key, e);
            }
        });
    }

    @Override
    public boolean isHotKey(String key) {
        if (key == null) {
            return false;
        }
        return hotKeys.contains(key);
    }

    @Override
    public Set<String> getHotKeys() {
        return Collections.unmodifiableSet(hotKeys);
    }

    @Override
    public void promoteHotKeys(Set<String> newHotKeys) {
        if (newHotKeys == null || newHotKeys.isEmpty()) {
            return;
        }
        synchronized (updateLock) {
            Set<String> updated = new HashSet<>(hotKeys);
            updated.addAll(newHotKeys);
            this.hotKeys = Collections.unmodifiableSet(updated);
            if (log.isDebugEnabled()) {
                log.debug("晋升热Key完成, 新晋升: {}, 新晋升key列表: {}, 总热Key数: {}",
                        newHotKeys.size(), newHotKeys, hotKeys.size());
            }
        }
    }

    @Override
    public void demoteHotKeys(Set<String> removedKeys) {
        if (removedKeys == null || removedKeys.isEmpty()) {
            return;
        }
        synchronized (updateLock) {
            Set<String> updated = new HashSet<>(hotKeys);
            updated.removeAll(removedKeys);
            this.hotKeys = Collections.unmodifiableSet(updated);
            
            // 清理被降级key的相关资源
            cleanupDemotedKeys(removedKeys);
            
            if (log.isDebugEnabled()) {
                log.debug("降级热Key完成, 移除: {}, 移除key列表: {}, 剩余热Key数: {}",
                        removedKeys.size(), removedKeys, hotKeys.size());
            }
        }
    }

    /**
     * 清理被降级key的相关资源
     * 包括：存储层数据、缓存数据更新器中的注册信息
     *
     * @param removedKeys 被降级的key集合
     */
    private void cleanupDemotedKeys(Set<String> removedKeys) {
        // 清理被降级key的存储层数据
        if (hotKeyStorage != null) {
            for (String key : removedKeys) {
                try {
                    hotKeyStorage.remove(key);
                } catch (Exception e) {
                    log.debug("清理被降级key的存储层数据失败, key: {}", key, e);
                }
            }
        }
        
        // 自动清理被降级key的相关资源（从注册表和失败计数中移除）
        if (cacheDataUpdater != null) {
            cacheDataUpdater.cleanupDemotedKeys(removedKeys);
        }
    }
}
