package cn.techwolf.datastar.hotkey.monitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Set;

/**
 * 热Key监控JMX MBean实现类
 * 通过JMX暴露监控数据，支持JConsole、VisualVM等工具查看
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class HotKeyMonitorMBean implements HotKeyMonitorMXBean {
    /**
     * MBean对象名称
     */
    private static final String MBEAN_NAME = "cn.techwolf.datastar.hotkey:type=HotKeyMonitor";

    /**
     * Jackson ObjectMapper实例（线程安全，可复用）
     * 使用与Spring Boot相同的JSON库
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 监控器实例
     */
    private final IHotKeyMonitor monitor;

    /**
     * 构造函数
     * 
     * 注意：MBean的注册由Spring的JMX自动配置管理，不需要在构造函数中手动注册
     * 这样可以避免重复注册的问题，并且Spring会自动处理MBean的生命周期
     *
     * @param monitor 监控器实例
     */
    public HotKeyMonitorMBean(IHotKeyMonitor monitor) {
        this.monitor = monitor;
        // 移除自动注册，由Spring的JMX自动配置管理
        // registerMBean();
    }

    /**
     * 注册MBean到MBeanServer
     * 
     * 注意：此方法已废弃，MBean的注册由Spring的JMX自动配置管理
     * 保留此方法仅用于向后兼容，实际不会被调用
     * 
     * @deprecated 使用Spring的JMX自动配置来注册MBean
     */
    @Deprecated
    private void registerMBean() {
        try {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName(MBEAN_NAME);
            if (!mBeanServer.isRegistered(objectName)) {
                mBeanServer.registerMBean(this, objectName);
                log.info("热Key监控MBean注册成功: {}", MBEAN_NAME);
            } else {
                log.warn("热Key监控MBean已存在: {}", MBEAN_NAME);
            }
        } catch (Exception e) {
            log.error("注册热Key监控MBean失败", e);
        }
    }

    /**
     * 注销MBean
     */
    public void unregisterMBean() {
        try {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName(MBEAN_NAME);
            if (mBeanServer.isRegistered(objectName)) {
                mBeanServer.unregisterMBean(objectName);
                log.info("热Key监控MBean注销成功: {}", MBEAN_NAME);
            }
        } catch (Exception e) {
            log.error("注销热Key监控MBean失败", e);
        }
    }

    @Override
    public int getHotKeyCount() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getHotKeyCount() : 0;
        } catch (Exception e) {
            log.error("获取热Key数量失败", e);
            return 0;
        }
    }

    @Override
    public String getHotKeys() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            if (info == null || info.getHotKeys() == null) {
                return "[]";
            }
            Set<String> hotKeys = info.getHotKeys();
            if (hotKeys.isEmpty()) {
                return "[]";
            }
            // 使用Jackson序列化，自动处理转义
            return OBJECT_MAPPER.writeValueAsString(hotKeys);
        } catch (JsonProcessingException e) {
            log.error("获取热Key列表失败", e);
            return "[]";
        } catch (Exception e) {
            log.error("获取热Key列表失败", e);
            return "[]";
        }
    }

    @Override
    public long getStorageSize() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getStorageSize() : 0L;
        } catch (Exception e) {
            log.error("获取数据存储层大小失败", e);
            return 0L;
        }
    }

    @Override
    public int getRecorderSize() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getRecorderSize() : 0;
        } catch (Exception e) {
            log.error("获取访问记录模块数据量失败", e);
            return 0;
        }
    }

    @Override
    public long getRecorderMemorySize() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getRecorderMemorySize() : 0L;
        } catch (Exception e) {
            log.error("获取访问记录模块内存大小失败", e);
            return 0L;
        }
    }

    @Override
    public long getTotalWrapGetCount() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getTotalWrapGetCount() : 0L;
        } catch (Exception e) {
            log.error("获取wrapGet总调用次数失败", e);
            return 0L;
        }
    }

    @Override
    public double getWrapGetQps() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getWrapGetQps() : 0.0;
        } catch (Exception e) {
            log.error("获取wrapGet的QPS失败", e);
            return 0.0;
        }
    }

    @Override
    public double getKeysPerSecond() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getKeysPerSecond() : 0.0;
        } catch (Exception e) {
            log.error("获取每秒访问的不同key数量失败", e);
            return 0.0;
        }
    }

    @Override
    public long getHotKeyAccessCount() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getHotKeyAccessCount() : 0L;
        } catch (Exception e) {
            log.error("获取热Key访问总次数失败", e);
            return 0L;
        }
    }

    @Override
    public long getHotKeyHitCount() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getHotKeyHitCount() : 0L;
        } catch (Exception e) {
            log.error("获取热Key缓存命中次数失败", e);
            return 0L;
        }
    }

    @Override
    public long getHotKeyMissCount() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getHotKeyMissCount() : 0L;
        } catch (Exception e) {
            log.error("获取热Key缓存未命中次数失败", e);
            return 0L;
        }
    }

    @Override
    public double getHotKeyHitRate() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getHotKeyHitRate() : 0.0;
        } catch (Exception e) {
            log.error("获取热Key命中率失败", e);
            return 0.0;
        }
    }

    @Override
    public double getHotKeyHitRatePercent() {
        return getHotKeyHitRate() * 100.0;
    }

    @Override
    public double getHotKeyTrafficRatio() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            return info != null ? info.getHotKeyTrafficRatio() : 0.0;
        } catch (Exception e) {
            log.error("获取热Key流量占比失败", e);
            return 0.0;
        }
    }

    @Override
    public double getHotKeyTrafficRatioPercent() {
        return getHotKeyTrafficRatio() * 100.0;
    }

    @Override
    public String getMonitorInfoJson() {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            if (info == null) {
                return "{}";
            }
            // 使用Jackson序列化，自动处理所有字段和转义
            return OBJECT_MAPPER.writeValueAsString(info);
        } catch (JsonProcessingException e) {
            log.error("获取监控信息失败", e);
            return "{}";
        } catch (Exception e) {
            log.error("获取监控信息失败", e);
            return "{}";
        }
    }

    @Override
    public void refresh() {
        try {
            // 强制刷新监控数据
            monitor.getMonitorInfo();
            log.info("监控数据刷新成功");
        } catch (Exception e) {
            log.error("刷新监控数据失败", e);
        }
    }

}
