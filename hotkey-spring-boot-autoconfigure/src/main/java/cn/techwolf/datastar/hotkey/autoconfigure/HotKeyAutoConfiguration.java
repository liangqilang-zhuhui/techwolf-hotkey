package cn.techwolf.datastar.hotkey.autoconfigure;

import cn.techwolf.datastar.hotkey.HotKeyClient;
import cn.techwolf.datastar.hotkey.IHotKeyClient;
import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.monitor.HotKeyMonitor;
import cn.techwolf.datastar.hotkey.monitor.HotKeyMonitorMBean;
import cn.techwolf.datastar.hotkey.monitor.HotKeyMetrics;
import cn.techwolf.datastar.hotkey.monitor.IHotKeyMonitor;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 热Key检测Spring Boot自动配置类
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
@Configuration
@ConditionalOnClass({StringRedisTemplate.class, HotKeyClient.class})
@ConditionalOnProperty(prefix = "hotkey", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(HotKeyProperties.class)
@EnableScheduling
public class HotKeyAutoConfiguration {

    private final HotKeyProperties properties;

    public HotKeyAutoConfiguration(HotKeyProperties properties) {
        this.properties = properties;
    }

    /**
     * 将HotKeyProperties转换为HotKeyConfig
     */
    @Bean
    @ConditionalOnMissingBean
    public HotKeyConfig hotKeyConfig() {
        HotKeyConfig config = new HotKeyConfig();
        config.setEnabled(properties.isEnabled());

        // 转换Detection配置
        HotKeyProperties.Detection detectionProps = properties.getDetection();
        HotKeyConfig.Detection detection = new HotKeyConfig.Detection();
        detection.setWindowSize(detectionProps.getWindowSize());
        detection.setTopN(detectionProps.getTopN());
        detection.setHotKeyQpsThreshold(detectionProps.getHotKeyQpsThreshold());
        detection.setWarmKeyQpsThreshold(detectionProps.getWarmKeyQpsThreshold());
        detection.setPromotionInterval(detectionProps.getPromotionInterval());
        detection.setDemotionInterval(detectionProps.getDemotionInterval());
        detection.setMaxStatsCapacity(detectionProps.getMaxStatsCapacity());
        detection.setAdmissionMinFrequency(detectionProps.getAdmissionMinFrequency());
        detection.setSamplingRate(detectionProps.getSamplingRate());
        detection.setFastAdmissionThreshold(detectionProps.getFastAdmissionThreshold());
        detection.setRejectedAccessThreshold(detectionProps.getRejectedAccessThreshold());
        detection.setEnableConsistentSampling(detectionProps.isEnableConsistentSampling());
        detection.setCapacityUsageThreshold(detectionProps.getCapacityUsageThreshold());
        config.setDetection(detection);

        // 转换Storage配置
        HotKeyProperties.Storage storageProps = properties.getStorage();
        HotKeyConfig.Storage storage = new HotKeyConfig.Storage();
        storage.setEnabled(storageProps.isEnabled());
        storage.setMaximumSize(storageProps.getMaximumSize());
        storage.setExpireAfterWrite(storageProps.getExpireAfterWrite());
        storage.setRecordStats(storageProps.isRecordStats());
        config.setStorage(storage);

        // 转换Recorder配置
        HotKeyProperties.Recorder recorderProps = properties.getRecorder();
        HotKeyConfig.Recorder recorder = new HotKeyConfig.Recorder();
        recorder.setMaxCapacity(recorderProps.getMaxCapacity());
        recorder.setWindowSize(recorderProps.getWindowSize());
        recorder.setInactiveExpireTime(recorderProps.getInactiveExpireTime());
        config.setRecorder(recorder);

        // 转换Monitor配置
        HotKeyProperties.Monitor monitorProps = properties.getMonitor();
        HotKeyConfig.Monitor monitor = new HotKeyConfig.Monitor();
        monitor.setInterval(monitorProps.getInterval());
        config.setMonitor(monitor);

        // 转换Refresh配置
        HotKeyProperties.Refresh refreshProps = properties.getRefresh();
        HotKeyConfig.Refresh refresh = new HotKeyConfig.Refresh();
        refresh.setEnabled(refreshProps.isEnabled());
        refresh.setInterval(refreshProps.getInterval());
        refresh.setMaxFailureCount(refreshProps.getMaxFailureCount());
        config.setRefresh(refresh);

        log.info("热Key检测配置初始化完成: enabled={}", config.isEnabled());
        return config;
    }

    /**
     * 热Key客户端
     * 注意：HotKeyClient内部会自己创建所有依赖组件（AccessRecorder、HotKeyStorage、HotKeySelector、HotKeyManager）
     * 所以这里直接传入config即可，HotKeyClient会自动初始化所有组件
     */
    @Bean
    @ConditionalOnMissingBean
    public IHotKeyClient hotKeyClient(HotKeyConfig config) {
        return new HotKeyClient(config);
    }

    /**
     * 热Key监控器
     * 注意：监控器作为HotKeyClient的内部属性，直接从HotKeyClient获取
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hotkey.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IHotKeyMonitor hotKeyMonitor(IHotKeyClient hotKeyClient) {
        IHotKeyMonitor monitor = hotKeyClient.getHotKeyMonitor();
        if (monitor == null) {
            log.warn("HotKeyClient未启用或监控器未初始化");
        }
        return monitor;
    }

    /**
     * 热Key监控JMX MBean
     * 通过JMX暴露监控数据，支持JConsole、VisualVM等工具查看
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hotkey.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HotKeyMonitorMBean hotKeyMonitorMBean(IHotKeyMonitor hotKeyMonitor) {
        if (hotKeyMonitor == null) {
            log.warn("热Key监控器未初始化，跳过JMX MBean注册");
            return null;
        }
        return new HotKeyMonitorMBean(hotKeyMonitor);
    }

    /**
     * 热Key监控Prometheus指标绑定器
     * 条件：只有当MeterRegistry存在时才注册（即项目已集成Micrometer）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean({IHotKeyMonitor.class, MeterRegistry.class})
    @ConditionalOnProperty(prefix = "hotkey.monitor.prometheus", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HotKeyMetrics hotKeyMetrics(IHotKeyMonitor hotKeyMonitor, 
                                       MeterRegistry meterRegistry,
                                       @Value("${spring.application.name:hotkey}") String applicationName) {
        if (hotKeyMonitor == null) {
            log.warn("热Key监控器未初始化，跳过Prometheus指标注册");
            return null;
        }
        HotKeyMetrics metrics = new HotKeyMetrics(meterRegistry, hotKeyMonitor, applicationName);
        metrics.bindTo();
        return metrics;
    }

    /**
     * 监控任务调度器（使用Spring的@Scheduled）
     */
    @Configuration
    @ConditionalOnProperty(prefix = "hotkey.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class MonitorScheduler {
        private final IHotKeyMonitor monitor;

        public MonitorScheduler(IHotKeyMonitor monitor) {
            this.monitor = monitor;
        }

        @Scheduled(fixedDelayString = "${hotkey.monitor.interval:60000}")
        public void scheduleMonitor() {
            if (monitor instanceof HotKeyMonitor) {
                ((HotKeyMonitor) monitor).monitor();
            }
        }
    }

    /**
     * Prometheus指标更新调度器
     * 定期更新Counter指标（Gauge指标会自动更新）
     */
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean({IHotKeyMonitor.class, HotKeyMetrics.class})
    @ConditionalOnProperty(prefix = "hotkey.monitor.prometheus", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class PrometheusMetricsScheduler {
        private final HotKeyMetrics hotKeyMetrics;
        
        public PrometheusMetricsScheduler(HotKeyMetrics hotKeyMetrics) {
            this.hotKeyMetrics = hotKeyMetrics;
        }
        
        /**
         * 定期更新Counter指标
         * 频率：每分钟更新一次（与监控任务同步）
         */
        @Scheduled(fixedDelayString = "${hotkey.monitor.interval:60000}")
        public void updateMetrics() {
            if (hotKeyMetrics != null) {
                hotKeyMetrics.updateCounters();
            }
        }
    }
}
