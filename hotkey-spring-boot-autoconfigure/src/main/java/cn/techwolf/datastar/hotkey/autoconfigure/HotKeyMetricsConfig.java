package cn.techwolf.datastar.hotkey.autoconfigure;

import cn.techwolf.datastar.hotkey.IHotKeyClient;
import cn.techwolf.datastar.hotkey.monitor.IHotKeyMonitor;
import cn.techwolf.datastar.hotkey.monitor.MonitorInfo;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * HotKey Prometheus指标配置类
 * 将HotKey监控指标注册到Micrometer，以便Prometheus采集
 * 
 * 使用方式：
 * 1. 在 application.yml 中配置：hotkey.monitor.prometheus.enabled=true（默认启用）
 * 2. 确保项目已引入 spring-boot-starter-actuator 和 micrometer-registry-prometheus
 * 3. 访问 http://localhost:8080/actuator/prometheus 查看指标
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
@Configuration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "hotkey.monitor.prometheus", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HotKeyMetricsConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired(required = false)
    private IHotKeyClient hotKeyClient;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Value("${spring.application.name:hotkey-demo}")
    private String applicationName;

    private boolean metricsRegistered = false;

    /**
     * 应用启动完成后注册HotKey指标到Micrometer
     *
     * @param event 应用就绪事件
     */
    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        if (metricsRegistered) {
            return;
        }
        registerMetrics();
    }

    /**
     * 注册HotKey指标到Micrometer
     */
    private void registerMetrics() {
        if (meterRegistry == null) {
            log.warn("MeterRegistry未找到，跳过HotKey指标注册");
            return;
        }
        
        if (hotKeyClient == null) {
            log.warn("HotKey客户端未找到，跳过指标注册");
            return;
        }
        
        if (!hotKeyClient.isEnabled()) {
            log.warn("HotKey客户端未启用，跳过指标注册");
            return;
        }

        IHotKeyMonitor monitor = hotKeyClient.getHotKeyMonitor();
        if (monitor == null) {
            log.warn("HotKey监控器未初始化，跳过指标注册");
            return;
        }

        List<Tag> commonTags = buildCommonTags();
        registerAllMetrics(monitor, commonTags);

        metricsRegistered = true;
        log.info("HotKey Prometheus指标注册成功，共注册16个指标");
    }

    /**
     * 构建通用标签
     *
     * @return 标签列表
     */
    private List<Tag> buildCommonTags() {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("application", applicationName != null ? applicationName : "hotkey-demo"));
        return tags;
    }

    /**
     * 注册所有指标
     *
     * @param monitor 监控器
     * @param tags    通用标签
     */
    private void registerAllMetrics(IHotKeyMonitor monitor, List<Tag> tags) {
        // 基础指标
        registerGauge("hotkey.count", "当前热Key数量", monitor, MonitorInfo::getHotKeyCount, tags);
        registerGauge("hotkey.storage.size", "本地缓存存储大小", monitor, MonitorInfo::getStorageSize, tags);
        
        // 记录器指标
        registerGauge("hotkey.recorder.size", "访问记录器大小", monitor, MonitorInfo::getRecorderSize, tags);
        registerGauge("hotkey.recorder.memory.size", "访问记录器内存大小（字节）", monitor, MonitorInfo::getRecorderMemorySize, tags);
        
        // 更新器指标
        registerGauge("hotkey.updater.size", "更新器大小", monitor, MonitorInfo::getUpdaterSize, tags);
        
        // 访问统计指标
        registerGauge("hotkey.total.access.count", "总访问次数（wrapGet调用次数）", monitor, MonitorInfo::getTotalWrapGetCount, tags);
        registerGauge("hotkey.qps", "当前QPS（每秒访问次数）", monitor, MonitorInfo::getWrapGetQps, tags);
        registerGauge("hotkey.keys.per.second", "每秒访问的Key数量", monitor, MonitorInfo::getKeysPerSecond, tags);
        
        // 热Key统计指标
        registerGauge("hotkey.hot.access.count", "热Key访问次数", monitor, MonitorInfo::getHotKeyAccessCount, tags);
        registerGauge("hotkey.hot.access.qps", "热Key访问QPS（每秒请求数）", monitor, MonitorInfo::getHotKeyAccessQps, tags);
        registerGauge("hotkey.hot.hit.count", "热Key缓存命中次数", monitor, MonitorInfo::getHotKeyHitCount, tags);
        registerGauge("hotkey.hot.hit.qps", "热Key访问命中QPS（每秒请求数，从本地缓存获取）", monitor, MonitorInfo::getHotKeyHitQps, tags);
        registerGauge("hotkey.hot.miss.count", "热Key缓存未命中次数", monitor, MonitorInfo::getHotKeyMissCount, tags);
        registerGauge("hotkey.hot.miss.qps", "热Key访问未命中QPS（每秒请求数，需要访问Redis）", monitor, MonitorInfo::getHotKeyMissQps, tags);
        registerGauge("hotkey.hit.rate", "热Key缓存命中率（0-1之间）", monitor, MonitorInfo::getHotKeyHitRate, tags);
        registerGauge("hotkey.traffic.ratio", "热Key流量占比（0-1之间）", monitor, MonitorInfo::getHotKeyTrafficRatio, tags);
    }

    /**
     * 注册Gauge指标
     *
     * @param name        指标名称
     * @param description 指标描述
     * @param monitor     监控器
     * @param getter       值获取函数
     * @param tags         标签列表
     */
    private void registerGauge(String name, String description, IHotKeyMonitor monitor, 
                               Function<MonitorInfo, Number> getter, List<Tag> tags) {
        Gauge.builder(name, () -> getMonitorInfo(monitor, getter))
                .description(description)
                .tags(tags)
                .register(meterRegistry);
    }

    /**
     * 安全获取监控信息
     *
     * @param monitor 监控器
     * @param getter  信息获取函数
     * @return 监控信息值，如果获取失败返回0
     */
    private Number getMonitorInfo(IHotKeyMonitor monitor, Function<MonitorInfo, Number> getter) {
        try {
            MonitorInfo info = monitor.getMonitorInfo();
            if (info == null) {
                return 0;
            }
            Number value = getter.apply(info);
            return value != null ? value : 0;
        } catch (Exception e) {
            log.debug("获取监控信息失败", e);
            return 0;
        }
    }
}
