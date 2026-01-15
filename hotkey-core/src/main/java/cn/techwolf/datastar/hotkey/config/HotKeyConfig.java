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
         * 统计窗口大小（秒）
         */
        private int windowSize = HotKeyConfigDefaults.Detection.WINDOW_SIZE;

        /**
         * Top N数量（用于筛选器，每5秒晋升Top N个热Key）
         */
        private int topN = HotKeyConfigDefaults.Detection.TOP_N;

        /**
         * 热Key访问频率阈值（次/秒）
         * 只有QPS >= 3000的key才能成为热Key
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

        /**
         * 筛选器降级和淘汰的探测间隔（毫秒）
         * 每分钟探测一次访问记录
         */
        private long demotionInterval = HotKeyConfigDefaults.Detection.DEMOTION_INTERVAL;

        /**
         * 统计信息最大容量（限制accessStats的大小，避免内存溢出）
         * 默认5000，建议设置为topN的100-500倍
         */
        private int maxStatsCapacity = HotKeyConfigDefaults.Detection.MAX_STATS_CAPACITY;

        /**
         * 准入最小访问频率阈值（次/秒）
         * 低于此频率的key使用采样机制，不是每次都记录
         * 默认10.0，表示每秒访问10次以下的key会被采样
         */
        private double admissionMinFrequency = HotKeyConfigDefaults.Detection.ADMISSION_MIN_FREQUENCY;

        /**
         * 采样率（0.0-1.0）
         * 对于低频率key，按此比例采样记录
         * 默认0.1，表示10%的低频率访问会被记录
         */
        private double samplingRate = HotKeyConfigDefaults.Detection.SAMPLING_RATE;

        /**
         * 快速准入阈值（次/秒）
         * 超过此频率的key直接准入，不进行采样
         * 默认50.0，表示每秒访问50次以上的key直接记录
         */
        private double fastAdmissionThreshold = HotKeyConfigDefaults.Detection.FAST_ADMISSION_THRESHOLD;

        /**
         * 被拒绝访问强制准入阈值
         * 当某个key被采样拒绝的次数达到此阈值时，强制准入
         * 默认10000，表示被拒绝10000次后强制准入
         * 这样可以避免高频率key因为采样被拒绝而永远无法被识别为热Key
         */
        private int rejectedAccessThreshold = HotKeyConfigDefaults.Detection.REJECTED_ACCESS_THRESHOLD;

        /**
         * 是否启用一致性采样
         * 如果启用，使用key的hash值进行采样，保证同一个key的采样结果一致
         * 这样可以避免同一个key在不同时间点的采样结果不一致
         * 默认true
         */
        private boolean enableConsistentSampling = HotKeyConfigDefaults.Detection.ENABLE_CONSISTENT_SAMPLING;

        /**
         * 容量使用率阈值（0.0-1.0）
         * 当容量使用率低于此阈值时，提高采样率
         * 默认0.5，表示容量使用率低于50%时，采样率提高2倍
         */
        private double capacityUsageThreshold = HotKeyConfigDefaults.Detection.CAPACITY_USAGE_THRESHOLD;
    }

    /**
     * 数据存储配置（模块二：只存储热key的value，1分钟过期）
     */
    @Data
    public static class Storage {
        /**
         * 是否启用数据存储
         */
        private boolean enabled = HotKeyConfigDefaults.Storage.ENABLED;

        /**
         * 最大缓存数量
         */
        private long maximumSize = HotKeyConfigDefaults.Storage.MAXIMUM_SIZE;

        /**
         * 写入后过期时间（秒）
         * 默认60秒（1分钟）
         */
        private int expireAfterWrite = HotKeyConfigDefaults.Storage.EXPIRE_AFTER_WRITE;

        /**
         * 是否记录统计信息
         */
        private boolean recordStats = HotKeyConfigDefaults.Storage.RECORD_STATS;
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
         * 是否启用自动刷新
         */
        private boolean enabled = HotKeyConfigDefaults.Refresh.ENABLED;

        /**
         * 刷新间隔（毫秒）
         * 默认10秒刷新一次
         */
        private long interval = HotKeyConfigDefaults.Refresh.INTERVAL;

        /**
         * 刷新失败重试次数
         * 连续失败超过此次数后，移除该热key
         */
        private int maxFailureCount = HotKeyConfigDefaults.Refresh.MAX_FAILURE_COUNT;
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
        if (detection.getDemotionInterval() <= 0) {
            throw new IllegalArgumentException("降级间隔必须大于0");
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
        if (refresh.getMaxFailureCount() <= 0) {
            throw new IllegalArgumentException("刷新失败重试次数必须大于0");
        }
    }
}
