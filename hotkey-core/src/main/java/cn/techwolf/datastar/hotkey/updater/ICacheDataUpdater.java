package cn.techwolf.datastar.hotkey.updater;

import java.util.Set;
import java.util.function.Function;

/**
 * 缓存数据更新器接口（模块六）
 * 职责：定义缓存数据更新器的标准接口，存储和管理每个Redis key对应的数据获取回调函数，并负责自动刷新缓存数据
 *
 * @author techwolf
 * @date 2024
 */
public interface ICacheDataUpdater {
    /**
     * 注册数据获取回调函数
     *
     * @param key Redis key
     * @param dataGetter 数据获取回调函数（Function，接收key参数，返回value）
     */
    void register(String key, Function<String, String> dataGetter);

    /**
     * 获取指定key的数据获取回调函数
     *
     * @param key Redis key
     * @return 数据获取回调函数，如果未注册返回null
     */
    Function<String, String> get(String key);

    /**
     * 移除指定key的数据获取回调函数
     *
     * @param key Redis key
     */
    void remove(String key);

    /**
     * 获取所有已注册的key
     *
     * @return 所有已注册的key集合
     */
    Set<String> getAllKeys();

    /**
     * 清空所有注册的数据获取回调函数
     */
    void clear();

    /**
     * 获取注册表大小
     *
     * @return 注册的key数量
     */
    int size();

    /**
     * 检查key是否已注册
     *
     * @param key Redis key
     * @return 如果已注册返回true，否则返回false
     */
    boolean contains(String key);

    /**
     * 启动自动刷新服务
     * 开始定时刷新热Key数据
     */
    void start();

    /**
     * 停止自动刷新服务
     * 停止定时刷新任务
     */
    void stop();

    /**
     * 检查服务是否运行中
     *
     * @return 是否运行中
     */
    boolean isRunning();

    /**
     * 清理被降级的key
     * 从注册表和失败计数中移除，但保留缓存（可能还有用）
     *
     * @param demotedKeys 被降级的key集合
     */
    void cleanupDemotedKeys(Set<String> demotedKeys);
}
