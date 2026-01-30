package cn.techwolf.datastar.hotkey.autoconfigure;

import cn.techwolf.datastar.hotkey.config.HotKeyConfigDefaults;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 热Key检测配置属性（Spring Boot配置绑定）
 *
 * @author techwolf
 * @date 2024
 */
@Data
@ConfigurationProperties(prefix = "hotkey")
public class HotKeyProperties {

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
     * Prometheus监控配置
     */
    private Prometheus prometheus = new Prometheus();

    /**
     * 检测配置
     */
    @Data
    public static class Detection {
        private int topN = HotKeyConfigDefaults.Detection.TOP_N;
        private double hotKeyQpsThreshold = HotKeyConfigDefaults.Detection.HOT_KEY_QPS_THRESHOLD;
        private double warmKeyQpsThreshold = HotKeyConfigDefaults.Detection.WARM_KEY_QPS_THRESHOLD;
        private long promotionInterval = HotKeyConfigDefaults.Detection.PROMOTION_INTERVAL;
    }

    /**
     * 数据存储配置
     */
    @Data
    public static class Storage {
        private long maximumSize = HotKeyConfigDefaults.Storage.MAXIMUM_SIZE;
        private int expireAfterWrite = HotKeyConfigDefaults.Storage.EXPIRE_AFTER_WRITE;
    }

    /**
     * 访问记录配置
     */
    @Data
    public static class Recorder {
        private int maxCapacity = HotKeyConfigDefaults.Recorder.MAX_CAPACITY;
        private int windowSize = HotKeyConfigDefaults.Recorder.WINDOW_SIZE;
        private int inactiveExpireTime = HotKeyConfigDefaults.Recorder.INACTIVE_EXPIRE_TIME;
    }

    /**
     * 监控配置
     */
    @Data
    public static class Monitor {
        private long interval = HotKeyConfigDefaults.Monitor.INTERVAL;
    }

    /**
     * 刷新配置（模块六：Getter自动刷新）
     */
    @Data
    public static class Refresh {
        private long interval = HotKeyConfigDefaults.Refresh.INTERVAL;
    }

    /**
     * Prometheus监控配置
     * 注意：此配置类目前主要用于配置绑定，实际启用控制通过 @ConditionalOnProperty 实现
     */
    @Data
    public static class Prometheus {
        /**
         * 是否启用Prometheus指标
         * 默认值：true
         */
        private boolean enabled = true;
    }
}
