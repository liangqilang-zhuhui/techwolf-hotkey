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
     * 清理访问统计表（recentQpsTable）
     * 包含两种触发机制：
     * 1. 时间触发：清理超过指定时间未访问的非活跃key
     * 2. 容量触发：当容量超限时，按QPS排序，移除QPS最低的key
     * 保留热Key和温Key，优先清理冷Key
     * 清理策略：保留80%的容量，清理20%
     * 在降级任务执行完后调用，清理访问统计表
     * 
     * 注意：此方法默认异步执行，不阻塞调用线程
     */
    void cleanupRecentQpsTable();

    void cleanupPromotionQueue();
    /**
     * 获取访问记录器的详细统计信息
     * 包含 PromotionQueue 和 RecentQps 的大小、内存使用情况等统计指标
     * 
     * @return 统计信息对象
     */
    RecorderStatistics getStatistics();
}
