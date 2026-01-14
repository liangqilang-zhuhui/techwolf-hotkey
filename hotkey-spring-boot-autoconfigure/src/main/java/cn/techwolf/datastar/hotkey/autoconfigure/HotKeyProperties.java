package cn.techwolf.datastar.hotkey.autoconfigure;

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
    private boolean enabled = true;

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
     * 检测配置
     */
    @Data
    public static class Detection {
        private int windowSize = 10;
        private int topN = 10;
        private double hotKeyQpsThreshold = 3000.0;
        private double warmKeyQpsThreshold = 500.0;
        private long promotionInterval = 5000;
        private long demotionInterval = 60000;
        private int maxStatsCapacity = 5000;
        private double admissionMinFrequency = 10.0;
        private double samplingRate = 0.1;
        private double fastAdmissionThreshold = 50.0;
        private int rejectedAccessThreshold = 10000;
        private boolean enableConsistentSampling = true;
        private double capacityUsageThreshold = 0.5;
    }

    /**
     * 数据存储配置
     */
    @Data
    public static class Storage {
        private boolean enabled = true;
        private long maximumSize = 100;
        private int expireAfterWrite = 60;
        private boolean recordStats = true;
    }

    /**
     * 访问记录配置
     */
    @Data
    public static class Recorder {
        private int maxCapacity = 10000;
        private int windowSize = 60;
    }

    /**
     * 监控配置
     */
    @Data
    public static class Monitor {
        private long interval = 60000;
        private boolean enabled = true;
    }
}
