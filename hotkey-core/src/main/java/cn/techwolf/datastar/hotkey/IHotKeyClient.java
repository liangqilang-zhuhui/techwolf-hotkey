package cn.techwolf.datastar.hotkey;

import java.util.function.Function;

/**
 * 热Key客户端接口
 * 职责：对外提供热Key缓存服务，专注于get操作的缓存优化
 * 设计：为RedisClientManager等业务方提供服务，只处理get操作相关的缓存逻辑
 * 
 * @author techwolf
 * @date 2024
 */
public interface IHotKeyClient {
    /**
     * 包装get操作，通过回调函数自动处理热Key缓存逻辑
     * 业务逻辑：
     * 1. 记录访问日志
     * 2. 如果是热Key，先从本地缓存获取
     * 3. 如果缓存未命中，调用redisGetter回调从Redis获取
     * 4. 如果从Redis获取到值，且是热Key，更新本地缓存
     *
     * @param key Redis key
     * @param redisGetter Redis获取值的回调函数（Function，接收key参数，返回value）
     * @return 缓存值或Redis值
     */
    String wrapGet(String key, Function<String, String> redisGetter);

    /**
     * 记录访问日志
     * 用于统计访问频率，用于热Key检测
     * 每次get操作都需要调用此方法记录访问
     *
     * @param key Redis key
     */
    void recordAccess(String key);

    /**
     * 更新热Key缓存
     * 业务逻辑：如果从Redis获取到值，且是热Key，则保存到本地缓存
     * 使用场景：从Redis获取到值后，如果是热Key，需要更新本地缓存
     *
     * @param key Redis key
     * @param value 从Redis获取的值（可能为null）
     */
    void updateCache(String key, String value);

    /**
     * 删除热Key缓存
     * 业务逻辑：如果是热Key，则从本地缓存中删除
     * 使用场景：删除Redis key时，如果是热Key，需要清理本地缓存
     *
     * @param key Redis key
     */
    void removeCache(String key);

    /**
     * 检查是否启用
     *
     * @return 是否启用
     */
    boolean isEnabled();

    /**
     * 获取热Key监控器（用于监控等场景）
     *
     * @return 热Key监控器，如果未启用则返回null
     */
    cn.techwolf.datastar.hotkey.monitor.IHotKeyMonitor getHotKeyMonitor();
}
