package cn.techwolf.datastar.hotkey.config;

import lombok.Data;

/**
 * 热Key检测配置（纯Java，不依赖Spring）
 *
 * @author techwolf
 * @date 2024
 */
@Data
public class HotKeyConfig {

    /**
     * 是否启用热Key检测
     */
    private boolean enabled = HotKeyConfigDefaults.ENABLED;

    /**
     * 检测配置
     */
    private Detection detection = new Detection();

    /**
     * 数据存储配置（模块二）
     */
    private Storage storage = new Storage();

    /**
     * 访问记录配置（模块三）
     */
    private Recorder recorder = new Recorder();

    /**
     * 监控配置（模块五）
     */
    private Monitor monitor = new Monitor();

    /**
     * 刷新配置（模块六：Getter自动刷新）
     */
    private Refresh refresh = new Refresh();

    /**
     * 检测配置
     */
    @Data
    public static class Detection {
        /**
         * Top N数量（用于筛选器，每5秒晋升Top N个热Key）
         */
        private int topN = HotKeyConfigDefaults.Detection.TOP_N;

        /**
         * 热Key访问频率阈值（次/秒）
         * 只有QPS >= 1000的key才能成为热Key
         */
        private double hotKeyQpsThreshold = HotKeyConfigDefaults.Detection.HOT_KEY_QPS_THRESHOLD;

        /**
         * 温Key访问频率阈值（次/秒）
         * 冷key需要每秒访问量大于500才可以升级到温key
         */
        private double warmKeyQpsThreshold = HotKeyConfigDefaults.Detection.WARM_KEY_QPS_THRESHOLD;

        /**
         * 筛选器晋升热Key的探测间隔（毫秒）
         * 每5秒探测一次访问记录
         */
        private long promotionInterval = HotKeyConfigDefaults.Detection.PROMOTION_INTERVAL;
    }

    /**
     * 数据存储配置（模块二：只存储热key的value，1分钟过期）
     */
    @Data
    public static class Storage {
        /**
         * 最大缓存数量
         */
        private long maximumSize = HotKeyConfigDefaults.Storage.MAXIMUM_SIZE;

        /**
         * 写入后过期时间（分钟）
         * 默认60分钟
         */
        private int expireAfterWrite = HotKeyConfigDefaults.Storage.EXPIRE_AFTER_WRITE;
    }

    /**
     * 访问记录配置（模块三：记录温、热key数据）
     */
    @Data
    public static class Recorder {
        /**
         * 访问记录最大容量
         * 限制访问记录的数量，避免内存溢出
         */
        private int maxCapacity = HotKeyConfigDefaults.Recorder.MAX_CAPACITY;

        /**
         * 统计窗口大小（秒）
         * 用于计算QPS
         */
        private int windowSize = HotKeyConfigDefaults.Recorder.WINDOW_SIZE;

        /**
         * 非活跃key过期时间（秒）
         * 如果一个key在指定时间内没有任何访问，将被自动移除
         * 默认60秒（1分钟）
         */
        private int inactiveExpireTime = HotKeyConfigDefaults.Recorder.INACTIVE_EXPIRE_TIME;
    }

    /**
     * 监控配置（模块五：监控器）
     */
    @Data
    public static class Monitor {
        /**
         * 监控输出间隔（毫秒）
         * 每分钟监控一次
         */
        private long interval = HotKeyConfigDefaults.Monitor.INTERVAL;
    }

    /**
     * 刷新配置（模块六：Getter自动刷新）
     */
    @Data
    public static class Refresh {
        /**
         * 刷新间隔（毫秒）
         * 默认10秒刷新一次
         */
        private long interval = HotKeyConfigDefaults.Refresh.INTERVAL;
    }

    /**
     * 验证配置参数
     * 检查配置的合理性，如果配置不合理会抛出IllegalArgumentException
     *
     * @throws IllegalArgumentException 如果配置不合理
     */
    public void validate() {
        if (detection == null) {
            throw new IllegalArgumentException("检测配置不能为null");
        }
        if (detection.getTopN() <= 0) {
            throw new IllegalArgumentException("TopN必须大于0");
        }
        if (detection.getHotKeyQpsThreshold() <= 0) {
            throw new IllegalArgumentException("热Key QPS阈值必须大于0");
        }
        if (detection.getPromotionInterval() <= 0) {
            throw new IllegalArgumentException("晋升间隔必须大于0");
        }
        if (storage == null) {
            throw new IllegalArgumentException("存储配置不能为null");
        }
        if (storage.getMaximumSize() <= 0) {
            throw new IllegalArgumentException("最大缓存数量必须大于0");
        }
        if (storage.getExpireAfterWrite() <= 0) {
            throw new IllegalArgumentException("过期时间必须大于0");
        }
        if (recorder == null) {
            throw new IllegalArgumentException("访问记录配置不能为null");
        }
        if (recorder.getMaxCapacity() <= 0) {
            throw new IllegalArgumentException("访问记录最大容量必须大于0");
        }
        if (refresh == null) {
            throw new IllegalArgumentException("刷新配置不能为null");
        }
        if (refresh.getInterval() <= 0) {
            throw new IllegalArgumentException("刷新间隔必须大于0");
        }
    }
}
