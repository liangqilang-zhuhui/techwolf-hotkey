package cn.techwolf.datastar.hotkey.storage;

/**
 * 热Key数据存储接口（模块二）
 * 职责：只存储热key的value，采用local cache，1分钟过期机制
 *
 * @author techwolf
 * @date 2024
 */
public interface IHotKeyStorage {
    /**
     * 获取热Key的值
     *
     * @param key Redis key
     * @return 缓存的值，如果不存在返回null
     */
    String get(String key);

    /**
     * 保存热Key的值
     *
     * @param key Redis key
     * @param value 值
     */
    void put(String key, String value);

    /**
     * 更新热Key的值
     *
     * @param key Redis key
     * @param value 值
     */
    void update(String key, String value);

    /**
     * 删除热Key的值
     *
     * @param key Redis key
     */
    void remove(String key);

    /**
     * 清空所有数据
     */
    void clear();

    /**
     * 获取存储大小
     *
     * @return 存储的key数量
     */
    long size();
}
