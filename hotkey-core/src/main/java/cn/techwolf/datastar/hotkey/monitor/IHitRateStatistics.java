package cn.techwolf.datastar.hotkey.monitor;

/**
 * 热Key命中率统计器接口
 * 职责：统计wrapGet的流量QPS、每秒key数量、热key命中数量、热key流量占比等
 *
 * @author techwolf
 * @date 2024
 */
public interface IHitRateStatistics {
    /**
     * 记录wrapGet调用（每次wrapGet调用时记录）
     *
     * @param key 访问的key
     */
    void recordWrapGet(String key);

    /**
     * 记录热Key访问（当key是热Key时调用）
     */
    void recordHotKeyAccess();

    /**
     * 记录热Key缓存命中（当从缓存获取到值时调用）
     */
    void recordHotKeyHit();

    /**
     * 记录热Key缓存未命中（当热Key从缓存未获取到值时调用）
     */
    void recordHotKeyMiss();

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
     * 命中率 = 命中次数 / (命中次数 + 未命中次数)
     *
     * @return 命中率，如果没有访问则返回0.0
     */
    double getHotKeyHitRate();

    /**
     * 获取热Key流量占比（0.0-1.0）
     * 热Key流量占比 = 热Key访问次数 / wrapGet总调用次数
     *
     * @return 热Key流量占比，如果没有访问则返回0.0
     */
    double getHotKeyTrafficRatio();

    /**
     * 获取热Key访问的QPS（每秒请求数）
     * 使用10秒滑动窗口统计热Key访问次数
     *
     * @return 热Key访问的QPS
     */
    double getHotKeyAccessQps();

    /**
     * 获取热Key访问命中的QPS（每秒请求数）
     * 使用10秒滑动窗口统计热Key缓存命中次数
     * 表示从本地缓存获取的QPS，性能最优
     *
     * @return 热Key访问命中的QPS
     */
    double getHotKeyHitQps();

    /**
     * 获取热Key访问未命中的QPS（每秒请求数）
     * 使用10秒滑动窗口统计热Key缓存未命中次数
     * 表示需要访问Redis的QPS
     *
     * @return 热Key访问未命中的QPS
     */
    double getHotKeyMissQps();

    /**
     * 重置统计数据
     */
    void reset();
}
