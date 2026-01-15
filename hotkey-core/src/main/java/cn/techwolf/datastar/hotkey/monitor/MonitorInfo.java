package cn.techwolf.datastar.hotkey.monitor;

import java.io.Serializable;
import java.util.Set;

/**
 * 热Key监控信息对象
 * 用于结构化存储监控采集的数据
 *
 * @author techwolf
 * @date 2024
 */
public class MonitorInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 热key列表
     */
    private Set<String> hotKeys;

    /**
     * 热key数量
     */
    private int hotKeyCount;

    /**
     * 数据存储层大小
     */
    private long storageSize;

    /**
     * 访问记录模块的数据量
     */
    private int recorderSize;

    /**
     * 访问记录模块的内存大小（估算，单位：字节）
     */
    private long recorderMemorySize;

    /**
     * 缓存数据更新器注册表的数据量
     */
    private int updaterSize;

    /**
     * 缓存数据更新器注册表的内存大小（估算，单位：字节）
     */
    private long updaterMemorySize;

    /**
     * wrapGet总调用次数
     */
    private long totalWrapGetCount;

    /**
     * wrapGet的QPS（每秒请求数）
     */
    private double wrapGetQps;

    /**
     * 每秒访问的不同key数量
     */
    private double keysPerSecond;

    /**
     * 热Key访问总次数
     */
    private long hotKeyAccessCount;

    /**
     * 热Key缓存命中次数
     */
    private long hotKeyHitCount;

    /**
     * 热Key缓存未命中次数
     */
    private long hotKeyMissCount;

    /**
     * 热Key命中率（0.0-1.0）
     */
    private double hotKeyHitRate;

    /**
     * 热Key流量占比（0.0-1.0）
     */
    private double hotKeyTrafficRatio;

    // Getters and Setters
    public Set<String> getHotKeys() {
        return hotKeys;
    }

    public void setHotKeys(Set<String> hotKeys) {
        this.hotKeys = hotKeys;
    }

    public int getHotKeyCount() {
        return hotKeyCount;
    }

    public void setHotKeyCount(int hotKeyCount) {
        this.hotKeyCount = hotKeyCount;
    }

    public long getStorageSize() {
        return storageSize;
    }

    public void setStorageSize(long storageSize) {
        this.storageSize = storageSize;
    }

    public int getRecorderSize() {
        return recorderSize;
    }

    public void setRecorderSize(int recorderSize) {
        this.recorderSize = recorderSize;
    }

    public long getRecorderMemorySize() {
        return recorderMemorySize;
    }

    public void setRecorderMemorySize(long recorderMemorySize) {
        this.recorderMemorySize = recorderMemorySize;
    }

    public int getUpdaterSize() {
        return updaterSize;
    }

    public void setUpdaterSize(int updaterSize) {
        this.updaterSize = updaterSize;
    }

    public long getUpdaterMemorySize() {
        return updaterMemorySize;
    }

    public void setUpdaterMemorySize(long updaterMemorySize) {
        this.updaterMemorySize = updaterMemorySize;
    }

    public long getTotalWrapGetCount() {
        return totalWrapGetCount;
    }

    public void setTotalWrapGetCount(long totalWrapGetCount) {
        this.totalWrapGetCount = totalWrapGetCount;
    }

    public double getWrapGetQps() {
        return wrapGetQps;
    }

    public void setWrapGetQps(double wrapGetQps) {
        this.wrapGetQps = wrapGetQps;
    }

    public double getKeysPerSecond() {
        return keysPerSecond;
    }

    public void setKeysPerSecond(double keysPerSecond) {
        this.keysPerSecond = keysPerSecond;
    }

    public long getHotKeyAccessCount() {
        return hotKeyAccessCount;
    }

    public void setHotKeyAccessCount(long hotKeyAccessCount) {
        this.hotKeyAccessCount = hotKeyAccessCount;
    }

    public long getHotKeyHitCount() {
        return hotKeyHitCount;
    }

    public void setHotKeyHitCount(long hotKeyHitCount) {
        this.hotKeyHitCount = hotKeyHitCount;
    }

    public long getHotKeyMissCount() {
        return hotKeyMissCount;
    }

    public void setHotKeyMissCount(long hotKeyMissCount) {
        this.hotKeyMissCount = hotKeyMissCount;
    }

    public double getHotKeyHitRate() {
        return hotKeyHitRate;
    }

    public void setHotKeyHitRate(double hotKeyHitRate) {
        this.hotKeyHitRate = hotKeyHitRate;
    }

    public double getHotKeyTrafficRatio() {
        return hotKeyTrafficRatio;
    }

    public void setHotKeyTrafficRatio(double hotKeyTrafficRatio) {
        this.hotKeyTrafficRatio = hotKeyTrafficRatio;
    }

    @Override
    public String toString() {
        return "MonitorInfo{" +
                "hotKeyCount=" + hotKeyCount +
                ", storageSize=" + storageSize +
                ", recorderSize=" + recorderSize +
                ", recorderMemorySize=" + recorderMemorySize +
                ", updaterSize=" + updaterSize +
                ", updaterMemorySize=" + updaterMemorySize +
                ", totalWrapGetCount=" + totalWrapGetCount +
                ", wrapGetQps=" + wrapGetQps +
                ", keysPerSecond=" + keysPerSecond +
                ", hotKeyAccessCount=" + hotKeyAccessCount +
                ", hotKeyHitCount=" + hotKeyHitCount +
                ", hotKeyMissCount=" + hotKeyMissCount +
                ", hotKeyHitRate=" + hotKeyHitRate +
                ", hotKeyTrafficRatio=" + hotKeyTrafficRatio +
                '}';
    }
}
