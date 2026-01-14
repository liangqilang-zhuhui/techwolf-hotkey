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
     * 获取温key列表（QPS >= 500 且 < 3000）
     *
     * @return 温key集合
     */
    Set<String> getWarmKeys();

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
     * 清理低QPS的key
     * 当容量超限时，按QPS排序，移除QPS最低的key
     * 保留热Key和温Key，优先清理冷Key
     * 清理策略：保留80%的容量，清理20%
     */
    void cleanupLowQpsKeys();
}
