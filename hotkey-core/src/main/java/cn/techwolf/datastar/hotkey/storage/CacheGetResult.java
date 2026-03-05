package cn.techwolf.datastar.hotkey.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 缓存获取结果
 * 封装缓存查询的命中状态和值
 *
 * @author techwolf
 * @date 2024
 */
@Getter
@AllArgsConstructor
public class CacheGetResult {
    /**
     * 是否命中缓存
     * true表示命中（包括缓存了null值和非null值），false表示未命中
     */
    private final boolean hit;

    /**
     * 缓存的值
     * 如果hit为true，value可能为null（表示缓存了null值）
     * 如果hit为false，value为null（表示缓存未命中）
     */
    private final String value;

    /**
     * 判断是否为null值命中
     * 
     * @return true表示缓存了null值，false表示缓存了非null值或未命中
     */
    public boolean isNullValueHit() {
        return hit && value == null;
    }

    /**
     * 判断是否为非null值命中
     * 
     * @return true表示缓存了非null值，false表示缓存了null值或未命中
     */
    public boolean isNonNullValueHit() {
        return hit && value != null;
    }
}
