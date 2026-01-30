package cn.techwolf.datastar.hotkey.monitor;

/**
 * 热Key监控JMX MBean接口
 * 用于通过JMX暴露监控数据
 *
 * @author techwolf
 * @date 2024
 */
public interface HotKeyMonitorMXBean {
    /**
     * 获取热Key数量
     *
     * @return 热Key数量
     */
    int getHotKeyCount();

    /**
     * 获取热Key列表（JSON格式字符串）
     *
     * @return 热Key列表的JSON字符串
     */
    String getHotKeys();

    /**
     * 获取数据存储层大小
     *
     * @return 数据存储层大小
     */
    long getStorageSize();

    /**
     * 获取访问记录模块的数据量
     *
     * @return 访问记录模块的数据量
     */
    int getRecorderSize();

    /**
     * 获取访问记录模块的内存大小（估算，单位：字节）
     *
     * @return 访问记录模块的内存大小
     */
    long getRecorderMemorySize();

    /**
     * 获取wrapGet总调用次数
     *
     * @return wrapGet总调用次数
     */
    long getTotalWrapGetCount();

    /**
     * 获取wrapGet的QPS（每秒请求数）
     *
     * @return wrapGet的QPS
     */
    double getWrapGetQps();

    /**
     * 获取每秒访问的不同key数量
     *
     * @return 每秒访问的不同key数量
     */
    double getKeysPerSecond();

    /**
     * 获取热Key访问总次数
     *
     * @return 热Key访问总次数
     */
    long getHotKeyAccessCount();

    /**
     * 获取热Key缓存命中次数
     *
     * @return 热Key缓存命中次数
     */
    long getHotKeyHitCount();

    /**
     * 获取热Key缓存未命中次数
     *
     * @return 热Key缓存未命中次数
     */
    long getHotKeyMissCount();

    /**
     * 获取热Key命中率（0.0-1.0）
     *
     * @return 热Key命中率
     */
    double getHotKeyHitRate();

    /**
     * 获取热Key命中率百分比（0.0-100.0）
     *
     * @return 热Key命中率百分比
     */
    double getHotKeyHitRatePercent();

    /**
     * 获取热Key流量占比（0.0-1.0）
     *
     * @return 热Key流量占比
     */
    double getHotKeyTrafficRatio();

    /**
     * 获取热Key流量占比百分比（0.0-100.0）
     *
     * @return 热Key流量占比百分比
     */
    double getHotKeyTrafficRatioPercent();

    /**
     * 获取完整的监控信息（JSON格式字符串）
     *
     * @return 监控信息的JSON字符串
     */
    String getMonitorInfoJson();

    /**
     * 刷新监控数据
     * 强制刷新一次监控数据采集
     */
    void refresh();
}
