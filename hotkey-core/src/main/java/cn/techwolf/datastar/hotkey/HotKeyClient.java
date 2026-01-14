package cn.techwolf.datastar.hotkey;

import lombok.extern.slf4j.Slf4j;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.factory.DefaultComponentFactory;
import cn.techwolf.datastar.hotkey.factory.IComponentFactory;
import cn.techwolf.datastar.hotkey.selector.IHotKeySelector;
import cn.techwolf.datastar.hotkey.updater.ICacheDataUpdater;
import cn.techwolf.datastar.hotkey.core.IHotKeyManager;
import cn.techwolf.datastar.hotkey.recorder.IAccessRecorder;
import cn.techwolf.datastar.hotkey.scheduler.IScheduler;
import cn.techwolf.datastar.hotkey.storage.IHotKeyStorage;

import java.util.function.Function;

/**
 * 热Key客户端实现
 * 职责：对外提供热Key缓存服务，封装所有热Key相关操作
 * 设计：不依赖Spring，直接new对象即可使用
 * 
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class HotKeyClient implements IHotKeyClient {
    /**
     * 热Key管理器
     */
    private final IHotKeyManager hotKeyManager;

    /**
     * 访问记录器
     */
    private final IAccessRecorder accessRecorder;

    /**
     * 热Key选择器
     */
    private final IHotKeySelector hotKeySelector;

    /**
     * 数据存储
     */
    private final IHotKeyStorage hotKeyStorage;

    /**
     * 缓存数据更新器
     */
    private final ICacheDataUpdater cacheDataUpdater;

    /**
     * 定时任务调度器
     */
    private final IScheduler scheduler;

    /**
     * 是否启用
     */
    private final boolean enabled;

    /**
     * 构造函数
     * 使用默认工厂初始化所有依赖组件，不依赖Spring
     *
     * @param config 配置参数
     */
    public HotKeyClient(HotKeyConfig config) {
        this(config, new DefaultComponentFactory());
    }

    /**
     * 构造函数
     * 支持自定义工厂，便于依赖注入和实现替换
     *
     * @param config 配置参数
     * @param factory 组件工厂
     */
    public HotKeyClient(HotKeyConfig config, IComponentFactory factory) {
        if (config == null) {
            throw new IllegalArgumentException("配置参数不能为null");
        }
        // 验证配置
        config.validate();
        this.enabled = config.isEnabled();

        if (!enabled) {
            log.info("热Key客户端未启用");
            this.hotKeyManager = null;
            this.accessRecorder = null;
            this.hotKeySelector = null;
            this.hotKeyStorage = null;
            this.cacheDataUpdater = null;
            this.scheduler = null;
            return;
        }

        if (factory == null) {
            factory = new DefaultComponentFactory();
        }

        // 1. 初始化访问记录器（模块三）
        this.accessRecorder = factory.createAccessRecorder(config);

        // 2. 初始化数据存储（模块二）
        this.hotKeyStorage = factory.createHotKeyStorage(config);

        // 3. 初始化热Key选择器（模块四）
        this.hotKeySelector = factory.createHotKeySelector(accessRecorder, config);

        // 4. 初始化缓存数据更新器（模块六，包含自动刷新功能）
        // 注意：需要在创建 HotKeyManager 之前创建，因为 Manager 需要依赖它
        this.cacheDataUpdater = factory.createCacheDataUpdater(hotKeyStorage, config);

        // 5. 初始化热Key管理器（模块一）
        // 注意：Manager 依赖 cacheDataUpdater 用于清理被降级key
        this.hotKeyManager = factory.createHotKeyManager(accessRecorder, hotKeyStorage, cacheDataUpdater);

        // 6. 初始化定时任务调度器
        this.scheduler = factory.createScheduler(config, hotKeyManager, hotKeySelector);

        // 9. 启动定时任务和刷新服务
        startServices();

        log.info("热Key客户端初始化完成，热Key检测: {}, 本地缓存: {}, 访问统计: {}",
                hotKeyManager != null, hotKeyStorage != null, accessRecorder != null);
    }

    /**
     * 启动所有服务
     * 包括定时任务调度器和缓存数据更新器的自动刷新服务
     */
    private void startServices() {
        if (!enabled) {
            return;
        }

        // 启动定时任务调度器
        if (scheduler != null) {
            scheduler.start();
        }

        // 启动缓存数据更新器的自动刷新服务（如果启用）
        if (cacheDataUpdater != null) {
            cacheDataUpdater.start();
        }
    }

    /**
     * 关闭客户端
     * 停止定时任务和缓存数据更新器的刷新服务，释放资源
     */
    public void shutdown() {
        // 停止缓存数据更新器的自动刷新服务
        if (cacheDataUpdater != null) {
            cacheDataUpdater.stop();
        }

        // 停止定时任务调度器
        if (scheduler != null) {
            scheduler.stop();
        }

        log.info("热Key客户端已关闭");
    }

    @Override
    public String wrapGet(String key, Function<String, String> redisGetter) {
        if (!enabled || key == null) {
            // 如果未启用或key为空，直接调用Redis获取
            if (log.isDebugEnabled()) {
                log.debug("热Key客户端未启用或key为空，直接从Redis获取, key: {}", key);
            }
            return redisGetter != null ? redisGetter.apply(key) : null;
        }

        // 1. 记录访问日志（用于热Key检测）
        recordAccess(key);

        // 2. 判断是否为热Key（需要多次检查，避免竞态条件）
        boolean isHotKey = isHotKey(key);
        if (log.isDebugEnabled()) {
            log.debug("开始获取key, key: {}, 是否为热Key: {}", key, isHotKey);
        }
        
        // 3. 如果是热Key，注册数据获取回调函数并尝试从缓存获取
        if (isHotKey) {
            // 注册数据获取回调函数到缓存数据更新器，用于自动刷新
            cacheDataUpdater.register(key, redisGetter);
            // 重新检查是否为热Key（避免在注册过程中被降级）
            if (isHotKey(key)) {
                String cachedValue = getFromCache(key);
                if (cachedValue != null) {
                    // 缓存命中，直接返回
                    if (log.isDebugEnabled()) {
                        log.debug("从本地缓存获取成功, key: {}, value: {}", key, cachedValue);
                    }
                    return cachedValue;
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("本地缓存未命中, key: {}, 从远程Redis获取", key);
        }

        // 3. 缓存未命中，通过回调从Redis获取值
        long startTime = System.currentTimeMillis();
        String value = redisGetter != null ? redisGetter.apply(key) : null;
        long cost = System.currentTimeMillis() - startTime;
        if (log.isDebugEnabled()) {
            if (value != null) {
                log.debug("从远程Redis获取成功, key: {}, value: {}, 耗时: {}ms", key, value, cost);
            } else {
                log.debug("从远程Redis获取失败或值为空, key: {}, 耗时: {}ms", key, cost);
            }
        }
        // 4. 如果从Redis获取到值，且是热Key，更新本地缓存
        // 重新检查是否为热Key（避免在获取Redis数据过程中被降级）
        if (value != null && isHotKey(key)) {
            updateCache(key, value);
            if (log.isDebugEnabled()) {
                log.debug("更新本地缓存完成, key: {}", key);
            }
        }
        return value;
    }

    @Override
    public void recordAccess(String key) {
        if (!enabled || key == null) {
            return;
        }
        hotKeyManager.recordAccess(key);
    }

    private String getFromCache(String key) {
        if (!enabled || key == null) {
            return null;
        }
        // 只有热Key才从缓存获取
        if (isHotKey(key)) {
            return hotKeyStorage.get(key);
        }
        return null;
    }

    @Override
    public void updateCache(String key, String value) {
        if (!enabled || key == null) {
            return;
        }
        // 如果是热Key，且值不为空，则更新缓存
        if (isHotKey(key) && value != null) {
            hotKeyStorage.put(key, value);
        }
    }

    /**
     * 判断是否为热Key（内部方法，用于getFromCache和updateCache）
     *
     * @param key Redis key
     * @return 是否为热Key
     */
    private boolean isHotKey(String key) {
        if (!enabled || key == null) {
            return false;
        }
        return hotKeyManager.isHotKey(key);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
