package cn.techwolf.datastar.hotkey.autoconfigure;

import cn.techwolf.datastar.hotkey.HotKeyClient;
import cn.techwolf.datastar.hotkey.IHotKeyClient;
import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.core.HotKeyManager;
import cn.techwolf.datastar.hotkey.core.IHotKeyManager;
import cn.techwolf.datastar.hotkey.selector.HotKeySelector;
import cn.techwolf.datastar.hotkey.selector.IHotKeySelector;
import cn.techwolf.datastar.hotkey.monitor.HotKeyMonitor;
import cn.techwolf.datastar.hotkey.monitor.IHotKeyMonitor;
import cn.techwolf.datastar.hotkey.recorder.AccessRecorder;
import cn.techwolf.datastar.hotkey.recorder.IAccessRecorder;
import cn.techwolf.datastar.hotkey.storage.HotKeyStorage;
import cn.techwolf.datastar.hotkey.storage.IHotKeyStorage;
import cn.techwolf.datastar.hotkey.updater.ICacheDataUpdater;
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
        config.setRecorder(recorder);

        // 转换Monitor配置
        HotKeyProperties.Monitor monitorProps = properties.getMonitor();
        HotKeyConfig.Monitor monitor = new HotKeyConfig.Monitor();
        monitor.setInterval(monitorProps.getInterval());
        config.setMonitor(monitor);

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
     * 注意：为了监控功能，我们需要从HotKeyClient中获取内部组件
     * 但由于HotKeyClient没有提供getter方法，我们直接创建监控器需要的组件
     * 这些组件会与HotKeyClient内部的组件重复，但只用于监控，不影响功能
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hotkey.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IHotKeyMonitor hotKeyMonitor(HotKeyConfig config) {
        // 创建监控所需的组件（这些组件与HotKeyClient内部的组件是独立的，仅用于监控）
        IAccessRecorder accessRecorder = new AccessRecorder(config);
        IHotKeyStorage hotKeyStorage = new HotKeyStorage(config);
        IHotKeySelector hotKeySelector = new HotKeySelector(accessRecorder, config);
        // 监控器不需要清理功能，传入null即可
        ICacheDataUpdater cacheDataUpdater = null;
        IHotKeyManager hotKeyManager = new HotKeyManager(accessRecorder, hotKeyStorage, cacheDataUpdater);
        return new HotKeyMonitor(hotKeyManager, hotKeyStorage, accessRecorder, config);
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
}
