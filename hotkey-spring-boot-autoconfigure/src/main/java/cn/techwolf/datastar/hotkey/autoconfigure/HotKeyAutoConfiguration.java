package cn.techwolf.datastar.hotkey.autoconfigure;

import cn.techwolf.datastar.hotkey.HotKeyClient;
import cn.techwolf.datastar.hotkey.IHotKeyClient;
import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.monitor.HotKeyMonitor;
import cn.techwolf.datastar.hotkey.monitor.HotKeyMonitorMBean;
import cn.techwolf.datastar.hotkey.monitor.IHotKeyMonitor;
import lombok.extern.slf4j.Slf4j;
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
     *
     * @return HotKeyConfig配置对象
     */
    @Bean
    @ConditionalOnMissingBean
    public HotKeyConfig hotKeyConfig() {
        HotKeyConfig config = new HotKeyConfig();
        config.setEnabled(properties.isEnabled());
        config.setDetection(convertDetectionConfig(properties.getDetection()));
        config.setStorage(convertStorageConfig(properties.getStorage()));
        config.setRecorder(convertRecorderConfig(properties.getRecorder()));
        config.setMonitor(convertMonitorConfig(properties.getMonitor()));
        config.setRefresh(convertRefreshConfig(properties.getRefresh()));

        log.info("热Key检测配置初始化完成: enabled={}, hotKeyQpsThreshold={}, warmKeyQpsThreshold={}, recorderMaxCapacity={}", 
            config.isEnabled(), 
            config.getDetection().getHotKeyQpsThreshold(), 
            config.getDetection().getWarmKeyQpsThreshold(),
            config.getRecorder().getMaxCapacity());
        return config;
    }

    /**
     * 转换Detection配置
     *
     * @param props 属性配置
     * @return Detection配置对象
     */
    private HotKeyConfig.Detection convertDetectionConfig(HotKeyProperties.Detection props) {
        HotKeyConfig.Detection detection = new HotKeyConfig.Detection();
        detection.setWindowSize(props.getWindowSize());
        detection.setTopN(props.getTopN());
        detection.setHotKeyQpsThreshold(props.getHotKeyQpsThreshold());
        detection.setWarmKeyQpsThreshold(props.getWarmKeyQpsThreshold());
        detection.setPromotionInterval(props.getPromotionInterval());
        detection.setDemotionInterval(props.getDemotionInterval());
        detection.setMaxStatsCapacity(props.getMaxStatsCapacity());
        detection.setAdmissionMinFrequency(props.getAdmissionMinFrequency());
        detection.setSamplingRate(props.getSamplingRate());
        detection.setFastAdmissionThreshold(props.getFastAdmissionThreshold());
        detection.setRejectedAccessThreshold(props.getRejectedAccessThreshold());
        detection.setEnableConsistentSampling(props.isEnableConsistentSampling());
        detection.setCapacityUsageThreshold(props.getCapacityUsageThreshold());
        return detection;
    }

    /**
     * 转换Storage配置
     *
     * @param props 属性配置
     * @return Storage配置对象
     */
    private HotKeyConfig.Storage convertStorageConfig(HotKeyProperties.Storage props) {
        HotKeyConfig.Storage storage = new HotKeyConfig.Storage();
        storage.setEnabled(props.isEnabled());
        storage.setMaximumSize(props.getMaximumSize());
        storage.setExpireAfterWrite(props.getExpireAfterWrite());
        storage.setRecordStats(props.isRecordStats());
        return storage;
    }

    /**
     * 转换Recorder配置
     *
     * @param props 属性配置
     * @return Recorder配置对象
     */
    private HotKeyConfig.Recorder convertRecorderConfig(HotKeyProperties.Recorder props) {
        HotKeyConfig.Recorder recorder = new HotKeyConfig.Recorder();
        recorder.setMaxCapacity(props.getMaxCapacity());
        recorder.setWindowSize(props.getWindowSize());
        recorder.setInactiveExpireTime(props.getInactiveExpireTime());
        return recorder;
    }

    /**
     * 转换Monitor配置
     *
     * @param props 属性配置
     * @return Monitor配置对象
     */
    private HotKeyConfig.Monitor convertMonitorConfig(HotKeyProperties.Monitor props) {
        HotKeyConfig.Monitor monitor = new HotKeyConfig.Monitor();
        monitor.setInterval(props.getInterval());
        return monitor;
    }

    /**
     * 转换Refresh配置
     *
     * @param props 属性配置
     * @return Refresh配置对象
     */
    private HotKeyConfig.Refresh convertRefreshConfig(HotKeyProperties.Refresh props) {
        HotKeyConfig.Refresh refresh = new HotKeyConfig.Refresh();
        refresh.setEnabled(props.isEnabled());
        refresh.setInterval(props.getInterval());
        refresh.setMaxFailureCount(props.getMaxFailureCount());
        return refresh;
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
     *
     * @param hotKeyClient 热Key客户端
     * @return 热Key监控器，如果未启用则返回null
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hotkey.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IHotKeyMonitor hotKeyMonitor(IHotKeyClient hotKeyClient) {
        if (hotKeyClient == null || !hotKeyClient.isEnabled()) {
            log.warn("HotKeyClient未启用，跳过监控器Bean注册");
            return null;
        }
        IHotKeyMonitor monitor = hotKeyClient.getHotKeyMonitor();
        if (monitor == null) {
            log.warn("HotKeyClient监控器未初始化");
        }
        return monitor;
    }

    /**
     * 热Key监控JMX MBean
     * 通过JMX暴露监控数据，支持JConsole、VisualVM等工具查看
     *
     * @param hotKeyMonitor 热Key监控器
     * @return JMX MBean，如果监控器未初始化则返回null
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
            if (monitor != null) {
                try {
                    monitor.getMonitorInfo();
                    if (monitor instanceof HotKeyMonitor) {
                        ((HotKeyMonitor) monitor).monitor();
                    }
                } catch (Exception e) {
                    log.error("监控任务执行失败", e);
                }
            }
        }
    }

}
