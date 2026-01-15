package cn.techwolf.datastar.hotkey.monitor;

import lombok.extern.slf4j.Slf4j;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.core.IHotKeyManager;
import cn.techwolf.datastar.hotkey.recorder.IAccessRecorder;
import cn.techwolf.datastar.hotkey.storage.IHotKeyStorage;
import cn.techwolf.datastar.hotkey.updater.ICacheDataUpdater;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Set;

/**
 * 热Key监控器实现（模块五）
 * 职责：每分钟监控，内存热key列表、数量；数据存储层大小；访问记录模块的数据量、大小
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class HotKeyMonitor implements IHotKeyMonitor {
    /**
     * 数字格式化器（保留2位小数）
     */
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.00");

    /**
     * 热Key管理器
     */
    private final IHotKeyManager hotKeyManager;

    /**
     * 数据存储
     */
    private final IHotKeyStorage hotKeyStorage;

    /**
     * 访问记录器
     */
    private final IAccessRecorder accessRecorder;

    /**
     * 缓存数据更新器
     */
    private final ICacheDataUpdater cacheDataUpdater;

    /**
     * 配置参数
     */
    private final HotKeyConfig config;

    /**
     * 命中率统计器
     */
    private final IHitRateStatistics hitRateStatistics;

    public HotKeyMonitor(IHotKeyManager hotKeyManager,
                        IHotKeyStorage hotKeyStorage,
                        IAccessRecorder accessRecorder,
                        ICacheDataUpdater cacheDataUpdater,
                        HotKeyConfig config,
                        IHitRateStatistics hitRateStatistics) {
        this.hotKeyManager = hotKeyManager;
        this.hotKeyStorage = hotKeyStorage;
        this.accessRecorder = accessRecorder;
        this.cacheDataUpdater = cacheDataUpdater;
        this.config = config;
        this.hitRateStatistics = hitRateStatistics;
    }

    /**
     * 每分钟监控（默认60秒）
     * 注意：定时任务由自动配置类管理，这里不添加@Scheduled注解
     */
    public void monitor() {
        if (!config.isEnabled()) {
            return;
        }
        try {
            MonitorInfo info = getMonitorInfo();

            // 输出监控日志
            log.info("========== 热Key监控统计 ==========");
            log.info("热Key数量: {}", info.getHotKeyCount());
            log.info("热Key列表: {}", info.getHotKeys());
            log.info("数据存储层大小: {}", info.getStorageSize());
            log.info("访问记录模块数据量: {}", info.getRecorderSize());
            log.info("访问记录模块内存大小(估算): {} bytes", info.getRecorderMemorySize());
            log.info("缓存数据更新器注册表数据量: {}", info.getUpdaterSize());
            log.info("缓存数据更新器注册表内存大小(估算): {} bytes", info.getUpdaterMemorySize());
            log.info("wrapGet总调用次数: {}", info.getTotalWrapGetCount());
            log.info("wrapGet的QPS: {}", formatDouble(info.getWrapGetQps()));
            log.info("每秒访问的不同key数量: {}", formatDouble(info.getKeysPerSecond()));
            log.info("热Key访问总次数: {}", info.getHotKeyAccessCount());
            log.info("热Key缓存命中次数: {}", info.getHotKeyHitCount());
            log.info("热Key缓存未命中次数: {}", info.getHotKeyMissCount());
            log.info("热Key命中率: {}%", formatDouble(info.getHotKeyHitRate() * 100));
            log.info("热Key流量占比: {}%", formatDouble(info.getHotKeyTrafficRatio() * 100));
            log.info("====================================");
        } catch (Exception e) {
            log.error("监控失败", e);
        }
    }

    /**
     * 格式化double值为保留2位小数的字符串
     *
     * @param value 要格式化的值
     * @return 格式化后的字符串
     */
    private String formatDouble(double value) {
        return DECIMAL_FORMAT.format(value);
    }

    @Override
    public MonitorInfo getMonitorInfo() {
        MonitorInfo info = new MonitorInfo();

        // 获取热key列表和数量
        if (hotKeyManager != null) {
            Set<String> hotKeys = hotKeyManager.getHotKeys();
            info.setHotKeys(hotKeys);
            info.setHotKeyCount(hotKeys.size());
        } else {
            info.setHotKeys(Collections.emptySet());
            info.setHotKeyCount(0);
        }

        // 获取数据存储层大小
        if (hotKeyStorage != null) {
            info.setStorageSize(hotKeyStorage.size());
        } else {
            info.setStorageSize(0);
        }

        // 获取访问记录模块的数据量和大小
        if (accessRecorder != null) {
            int recorderSize = accessRecorder.size();
            info.setRecorderSize(recorderSize);
            // 估算内存大小（每个key约100字节）
            info.setRecorderMemorySize(recorderSize * 100L);
        } else {
            info.setRecorderSize(0);
            info.setRecorderMemorySize(0L);
        }

        // 获取缓存数据更新器注册表的数据量和大小
        if (cacheDataUpdater != null) {
            int updaterSize = cacheDataUpdater.size();
            info.setUpdaterSize(updaterSize);
            // 估算内存大小（每个key和Function对象约200字节）
            info.setUpdaterMemorySize(updaterSize * 200L);
        } else {
            info.setUpdaterSize(0);
            info.setUpdaterMemorySize(0L);
        }

        // 获取命中率统计数据
        if (hitRateStatistics != null) {
            info.setTotalWrapGetCount(hitRateStatistics.getTotalWrapGetCount());
            info.setWrapGetQps(hitRateStatistics.getWrapGetQps());
            info.setKeysPerSecond(hitRateStatistics.getKeysPerSecond());
            info.setHotKeyAccessCount(hitRateStatistics.getHotKeyAccessCount());
            info.setHotKeyHitCount(hitRateStatistics.getHotKeyHitCount());
            info.setHotKeyMissCount(hitRateStatistics.getHotKeyMissCount());
            info.setHotKeyHitRate(hitRateStatistics.getHotKeyHitRate());
            info.setHotKeyTrafficRatio(hitRateStatistics.getHotKeyTrafficRatio());
        } else {
            info.setTotalWrapGetCount(0);
            info.setWrapGetQps(0.0);
            info.setKeysPerSecond(0.0);
            info.setHotKeyAccessCount(0);
            info.setHotKeyHitCount(0);
            info.setHotKeyMissCount(0);
            info.setHotKeyHitRate(0.0);
            info.setHotKeyTrafficRatio(0.0);
        }
        
        return info;
    }
}
