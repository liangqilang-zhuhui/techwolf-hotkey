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
        public static final int TOP_N = 20;
        public static final int HOT_KEY_QPS_THRESHOLD = 500;
        public static final int WARM_KEY_QPS_THRESHOLD = 200;
        public static final long PROMOTION_INTERVAL = 5000L;
    }

    /**
     * 数据存储配置（Storage）默认值
     */
    public static final class Storage {
        public static final long MAXIMUM_SIZE = Detection.TOP_N * 100;
        public static final int EXPIRE_AFTER_WRITE = 60;
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
    }

    /**
     * 刷新配置（Refresh）默认值
     */
    public static final class Refresh {
        public static final long INTERVAL = 10000L;
    }
}
