package cn.techwolf.datastar.hotkey.monitor;

import lombok.extern.slf4j.Slf4j;

import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.core.IHotKeyManager;
import cn.techwolf.datastar.hotkey.recorder.IAccessRecorder;
import cn.techwolf.datastar.hotkey.storage.IHotKeyStorage;

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
     * 配置参数
     */
    private final HotKeyConfig config;

    public HotKeyMonitor(IHotKeyManager hotKeyManager,
                        IHotKeyStorage hotKeyStorage,
                        IAccessRecorder accessRecorder,
                        HotKeyConfig config) {
        this.hotKeyManager = hotKeyManager;
        this.hotKeyStorage = hotKeyStorage;
        this.accessRecorder = accessRecorder;
        this.config = config;
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
            log.info("====================================");
        } catch (Exception e) {
            log.error("监控失败", e);
        }
    }

    @Override
    public MonitorInfo getMonitorInfo() {
        MonitorInfo info = new MonitorInfo();

        // 获取热key列表和数量
        Set<String> hotKeys = hotKeyManager.getHotKeys();
        info.setHotKeys(hotKeys);
        info.setHotKeyCount(hotKeys.size());

        // 获取数据存储层大小
        info.setStorageSize(hotKeyStorage.size());

        // 获取访问记录模块的数据量和大小
        int recorderSize = accessRecorder.size();
        info.setRecorderSize(recorderSize);
        // 估算内存大小（每个key约100字节）
        info.setRecorderMemorySize(recorderSize * 100L);

        return info;
    }
}
