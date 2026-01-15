package cn.techwolf.datastar.hotkey.config;

/**
 * 热Key检测配置默认值常量类
 * 统一管理所有配置项的默认值，确保HotKeyConfig和HotKeyProperties使用相同的默认值
 *
 * @author techwolf
 * @date 2024
 */
public final class HotKeyConfigDefaults {

    private HotKeyConfigDefaults() {
        // 工具类，禁止实例化
    }

    /**
     * 主配置默认值
     */
    public static final boolean ENABLED = true;

    /**
     * 检测配置（Detection）默认值
     */
    public static final class Detection {
        public static final int WINDOW_SIZE = 10;
        public static final int TOP_N = 20;
        public static final double HOT_KEY_QPS_THRESHOLD = 1000.0;
        public static final double WARM_KEY_QPS_THRESHOLD = 500.0;
        public static final long PROMOTION_INTERVAL = 5000L;
        public static final long DEMOTION_INTERVAL = 60000L;
        public static final int MAX_STATS_CAPACITY = 50000;
        public static final double ADMISSION_MIN_FREQUENCY = 10.0;
        public static final double SAMPLING_RATE = 0.1;
        public static final double FAST_ADMISSION_THRESHOLD = 50.0;
        public static final int REJECTED_ACCESS_THRESHOLD = 10000;
        public static final boolean ENABLE_CONSISTENT_SAMPLING = true;
        public static final double CAPACITY_USAGE_THRESHOLD = 0.5;
    }

    /**
     * 数据存储配置（Storage）默认值
     */
    public static final class Storage {
        public static final boolean ENABLED = true;
        public static final long MAXIMUM_SIZE = 200L;
        public static final int EXPIRE_AFTER_WRITE = 60;
        public static final boolean RECORD_STATS = true;
    }

    /**
     * 访问记录配置（Recorder）默认值
     */
    public static final class Recorder {
        public static final int MAX_CAPACITY = 100000;
        public static final int WINDOW_SIZE = 10;
        public static final int INACTIVE_EXPIRE_TIME = 120;
    }

    /**
     * 监控配置（Monitor）默认值
     */
    public static final class Monitor {
        public static final long INTERVAL = 60000L;
        public static final boolean ENABLED = true;
    }

    /**
     * 刷新配置（Refresh）默认值
     */
    public static final class Refresh {
        public static final boolean ENABLED = true;
        public static final long INTERVAL = 10000L;
        public static final int MAX_FAILURE_COUNT = 3;
    }
}
