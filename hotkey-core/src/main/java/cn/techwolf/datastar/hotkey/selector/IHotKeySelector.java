package cn.techwolf.datastar.hotkey.selector;

import java.util.Set;

/**
 * 热Key选择器接口（模块四）
 * 职责：根据访问统计选择哪些key应该晋升为热Key或从热Key中移除
 * 设计：无状态，只负责计算和选择，不维护热Key列表
 *
 * @author techwolf
 * @date 2024
 */
public interface IHotKeySelector {
    /**
     * 晋升热Key（每5秒执行一次）
     * 根据访问统计计算哪些key应该晋升为热Key
     * 将QPS top 10的热key，并且每秒访问大于3000，同时满足，则晋升为热key
     *
     * @param currentHotKeys 当前热Key列表（用于排除已经是热Key的）
     * @return 应该晋升的热key集合
     */
    Set<String> promoteHotKeys(Set<String> currentHotKeys);

    /**
     * 降级和淘汰（每分钟执行一次）
     * 根据访问统计计算哪些key应该从热Key中移除
     * 1. 热key中访问小于3000的，从热key移除
     * 2. 如果容量超限，则LRU进行淘汰访问量最低的key
     *
     * @param currentHotKeys 当前热Key列表
     * @return 应该被移除的热key集合
     */
    Set<String> demoteAndEvict(Set<String> currentHotKeys);
}
