package cn.techwolf.datastar.hotkey.core;

import java.util.Set;

/**
 * 热Key管理器接口（模块一）
 * 职责：
 * 1. 记录访问日志
 * 2. 维护和管理热Key列表
 * 3. 判断是否热key
 * 4. 与Redis Client集成，处理get/set操作
 *
 * @author techwolf
 * @date 2024
 */
public interface IHotKeyManager {
    /**
     * 记录访问日志
     *
     * @param key Redis key
     */
    void recordAccess(String key);

    /**
     * 判断是否为热Key
     *
     * @param key Redis key
     * @return 是否为热Key
     */
    boolean isHotKey(String key);

    /**
     * 获取当前热Key列表
     *
     * @return 热Key集合
     */
    Set<String> getHotKeys();

    /**
     * 晋升热Key
     * 将新的热Key添加到列表中
     *
     * @param newHotKeys 新晋升的热Key集合
     */
    void promoteHotKeys(Set<String> newHotKeys);

    /**
     * 降级热Key
     * 从列表中移除指定的热Key
     *
     * @param removedKeys 被移除的热Key集合
     */
    void demoteHotKeys(Set<String> removedKeys);
}
