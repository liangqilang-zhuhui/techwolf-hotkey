package cn.techwolf.datastar.hotkey.recorder;

import java.util.Map;
import java.util.Set;

/**
 * 访问记录器接口（模块三）
 * 职责：记录温、热key数据
 * 规则：冷key需要每秒访问量大于500才可以升级到温key
 *
 * @author techwolf
 * @date 2024
 */
public interface IAccessRecorder {
    /**
     * 记录访问
     *
     * @param key Redis key
     */
    void recordAccess(String key);

    /**
     * 获取所有温、热key的访问统计
     * key -> QPS（每秒访问量）
     *
     * @return 访问统计信息
     */
    Map<String, Double> getAccessStatistics();

    /**
     * 获取热key列表（QPS >= 3000）
     *
     * @return 热key集合
     */
    Set<String> getHotKeys();

    /**
     * 获取访问记录数量
     *
     * @return 记录数量
     */
    int size();

    /**
     * 清理过期和低QPS的key
     * 包含两种触发机制：
     * 1. 时间触发：清理超过指定时间未访问的非活跃key
     * 2. 容量触发：当容量超限时，按QPS排序，移除QPS最低的key
     * 保留热Key和温Key，优先清理冷Key
     * 清理策略：保留80%的容量，清理20%
     * 
     * 注意：此方法默认异步执行，不阻塞调用线程
     */
    void cleanupKeys();

    /**
     * 估算访问记录器的内存使用大小（字节）
     * 使用基于平均key长度的采样估算，性能优化：只采样前100个key
     * 
     * @return 估算的内存大小（字节）
     */
    long getMemorySize();
}
