package cn.techwolf.datastar.hotkey.monitor;

/**
 * 热Key监控器接口（模块五）
 * 职责：每分钟监控，内存热key列表、数量；数据存储层大小；访问记录模块的数据量、大小
 *
 * @author techwolf
 * @date 2024
 */
public interface IHotKeyMonitor {
    /**
     * 获取监控信息
     *
     * @return 监控信息对象
     */
    MonitorInfo getMonitorInfo();
}
