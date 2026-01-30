package cn.techwolf.datastar.hotkey;

import lombok.extern.slf4j.Slf4j;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.factory.DefaultComponentFactory;
import cn.techwolf.datastar.hotkey.factory.IComponentFactory;
import cn.techwolf.datastar.hotkey.selector.IHotKeySelector;
import cn.techwolf.datastar.hotkey.core.IHotKeyManager;
import cn.techwolf.datastar.hotkey.recorder.IAccessRecorder;
import cn.techwolf.datastar.hotkey.scheduler.IScheduler;
import cn.techwolf.datastar.hotkey.storage.IHotKeyStorage;
import cn.techwolf.datastar.hotkey.monitor.HotKeyMonitor;
import cn.techwolf.datastar.hotkey.monitor.IHotKeyMonitor;
import cn.techwolf.datastar.hotkey.monitor.HitRateStatistics;
import cn.techwolf.datastar.hotkey.monitor.IHitRateStatistics;
import cn.techwolf.datastar.hotkey.storage.HotKeyStorage;

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
     * 定时任务调度器
     */
    private final IScheduler scheduler;

    /**
     * 热Key监控器
     */
    private final HotKeyMonitor hotKeyMonitor;

    /**
     * 命中率统计器
     */
    private final IHitRateStatistics hitRateStatistics;

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
            this.scheduler = null;
            this.hotKeyMonitor = null;
            this.hitRateStatistics = null;
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

        // 5. 初始化热Key管理器（模块一）
        this.hotKeyManager = factory.createHotKeyManager(accessRecorder, hotKeyStorage);

        // 6. 初始化定时任务调度器（线程合并优化：将刷新任务合并到调度器中）
        this.scheduler = factory.createScheduler(config, hotKeyManager, hotKeySelector, accessRecorder);

        // 7. 初始化命中率统计器
        this.hitRateStatistics = new HitRateStatistics();

        // 8. 初始化热Key监控器（传入统计器）
        this.hotKeyMonitor = new HotKeyMonitor(hotKeyManager, hotKeyStorage, accessRecorder, config, hitRateStatistics);

        // 9. 启动定时任务和刷新服务
        startServices();

        log.info("热Key客户端初始化完成，热Key检测: {}, 本地缓存: {}, 访问统计: {}",
                hotKeyManager != null, hotKeyStorage != null, accessRecorder != null);
    }

    /**
     * 启动所有服务
     * 包括定时任务调度器（已合并刷新任务，不再单独启动刷新服务）
     */
    private void startServices() {
        if (!enabled) {
            return;
        }
        // 启动定时任务调度器（已包含晋升、降级、刷新任务）
        scheduler.start();
    }

    /**
     * 关闭客户端
     * 停止定时任务（已包含刷新任务），释放资源
     */
    public void shutdown() {
        // 停止定时任务调度器（已包含刷新任务）
        if (scheduler != null) {
            scheduler.stop();
        }
        // 关闭数据存储，释放刷新线程池资源
        if (hotKeyStorage != null) {
            hotKeyStorage.shutdown();
        }
        log.info("热Key客户端已关闭");
    }

    @Override
    public String wrapGet(String key, Function<String, String> redisGetter) {
        if (!enabled || key == null) {
            return redisGetter != null ? redisGetter.apply(key) : null;
        }

        // 0. 记录wrapGet调用统计
        hitRateStatistics.recordWrapGet(key);

        // 1. 记录访问日志（用于热Key检测）
        recordAccess(key);

        // 2. 检查是否为热Key
        boolean isHot = isHotKey(key);
        
        // 3. 如果是热Key，尝试从缓存获取
        if (isHot) {
            // 记录热Key访问
            hitRateStatistics.recordHotKeyAccess();
            // 从缓存获取（再次检查是否为热Key，避免在检查后被降级导致的竞态条件）
            String cachedValue = getFromCache(key);
            if (cachedValue != null) {
                // 记录热Key缓存命中
                hitRateStatistics.recordHotKeyHit();
                return cachedValue;
            } else {
                // 记录热Key缓存未命中
                hitRateStatistics.recordHotKeyMiss();
            }
        }

        // 4. 缓存未命中或key已被降级，从Redis获取值
        String value = redisGetter != null ? redisGetter.apply(key) : null;
        
        // 5. 如果从Redis获取到值，且是热Key，更新本地缓存
        // 再次检查是否为热Key（可能在获取Redis数据期间被降级）
        if (value != null && isHotKey(key)) {
            hotKeyStorage.put(key, value, redisGetter);
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

    /**
     * 从缓存获取值（内部方法，不检查是否为热Key）
     * 注意：调用此方法前需要确保key是热Key
     *
     * @param key Redis key
     * @return 缓存值，如果未命中返回null
     */
    private String getFromCache(String key) {
        if (!enabled || key == null) {
            return null;
        }
        // 直接获取，不重复检查是否为热Key（调用方已检查）
        return hotKeyStorage.get(key);
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

    @Override
    public IHotKeyMonitor getHotKeyMonitor() {
        return hotKeyMonitor;
    }
}
