package cn.techwolf.datastar.hotkey.monitor;

import cn.techwolf.datastar.hotkey.recorder.RecorderStatistics;

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
     * 访问记录模块的总数据量（PromotionQueue + RecentQps）
     */
    private int recorderSize;

    /**
     * 访问记录模块的总内存大小（PromotionQueue + RecentQps，单位：字节）
     */
    private long recorderMemorySize;

    /**
     * 访问记录模块的详细统计信息
     * 包含 PromotionQueue 和 RecentQps 的大小、内存使用情况等统计指标
     */
    private RecorderStatistics recorderStatistics;

    /**
     * 缓存数据更新器注册表的数据量
     */
    private int updaterSize;

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

    /**
     * 热Key访问的QPS（每秒请求数）
     */
    private double hotKeyAccessQps;

    /**
     * 热Key访问命中的QPS（每秒请求数）
     * 表示从本地缓存获取的QPS，性能最优
     */
    private double hotKeyHitQps;

    /**
     * 热Key访问未命中的QPS（每秒请求数）
     * 表示需要访问Redis的QPS
     */
    private double hotKeyMissQps;

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

    public RecorderStatistics getRecorderStatistics() {
        return recorderStatistics;
    }

    public void setRecorderStatistics(RecorderStatistics recorderStatistics) {
        this.recorderStatistics = recorderStatistics;
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

    public double getHotKeyAccessQps() {
        return hotKeyAccessQps;
    }

    public void setHotKeyAccessQps(double hotKeyAccessQps) {
        this.hotKeyAccessQps = hotKeyAccessQps;
    }

    public double getHotKeyHitQps() {
        return hotKeyHitQps;
    }

    public void setHotKeyHitQps(double hotKeyHitQps) {
        this.hotKeyHitQps = hotKeyHitQps;
    }

    public double getHotKeyMissQps() {
        return hotKeyMissQps;
    }

    public void setHotKeyMissQps(double hotKeyMissQps) {
        this.hotKeyMissQps = hotKeyMissQps;
    }

    @Override
    public String toString() {
        return "MonitorInfo{" +
                "hotKeyCount=" + hotKeyCount +
                ", storageSize=" + storageSize +
                ", recorderSize=" + recorderSize +
                ", recorderMemorySize=" + recorderMemorySize +
                ", recorderStatistics=" + recorderStatistics +
                ", updaterSize=" + updaterSize +
                ", totalWrapGetCount=" + totalWrapGetCount +
                ", wrapGetQps=" + wrapGetQps +
                ", keysPerSecond=" + keysPerSecond +
                ", hotKeyAccessCount=" + hotKeyAccessCount +
                ", hotKeyHitCount=" + hotKeyHitCount +
                ", hotKeyMissCount=" + hotKeyMissCount +
                ", hotKeyHitRate=" + hotKeyHitRate +
                ", hotKeyTrafficRatio=" + hotKeyTrafficRatio +
                ", hotKeyAccessQps=" + hotKeyAccessQps +
                ", hotKeyHitQps=" + hotKeyHitQps +
                ", hotKeyMissQps=" + hotKeyMissQps +
                '}';
    }
}
