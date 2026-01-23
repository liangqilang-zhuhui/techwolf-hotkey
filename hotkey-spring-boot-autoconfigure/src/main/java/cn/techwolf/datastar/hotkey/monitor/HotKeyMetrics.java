package cn.techwolf.datastar.hotkey.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 热Key监控Prometheus指标绑定器
 * 职责：将热Key监控数据暴露为Prometheus指标
 * 
 * 设计原则：
 * 1. 使用可选依赖，如果项目没有Micrometer，不影响运行
 * 2. 复用现有的MonitorInfo数据源，避免重复采集
 * 3. 使用Gauge和Counter两种指标类型，符合Prometheus最佳实践
 * 
 * @author techwolf
 * @date 2024
 */
@Slf4j
public class HotKeyMetrics {
    
    /**
     * MeterRegistry实例（Micrometer的核心接口）
     */
    private final MeterRegistry meterRegistry;
    
    /**
     * 监控器实例
     */
    private final IHotKeyMonitor monitor;
    
    /**
     * 指标标签（用于区分不同实例）
     */
    private final Tags tags;
    
    // ========== Gauge指标（当前值） ==========
    private Gauge hotKeyCountGauge;
    private Gauge storageSizeGauge;
    private Gauge recorderSizeGauge;
    private Gauge recorderMemorySizeGauge;
    private Gauge updaterSizeGauge;
    private Gauge updaterMemorySizeGauge;
    private Gauge wrapGetQpsGauge;
    private Gauge keysPerSecondGauge;
    private Gauge hotKeyHitRateGauge;
    private Gauge hotKeyTrafficRatioGauge;
    
    // ========== Counter指标（累计值） ==========
    private Counter totalWrapGetCounter;
    private Counter hotKeyAccessCounter;
    private Counter hotKeyHitCounter;
    private Counter hotKeyMissCounter;
    
    /**
     * 上次统计的累计值（用于计算增量）
     */
    private final AtomicReference<MonitorInfo> lastMonitorInfo = new AtomicReference<>();
    
    /**
     * 构造函数
     * 
     * @param meterRegistry MeterRegistry实例
     * @param monitor 监控器实例
     * @param applicationName 应用名称（用于标签）
     */
    public HotKeyMetrics(MeterRegistry meterRegistry, 
                        IHotKeyMonitor monitor,
                        String applicationName) {
        this.meterRegistry = meterRegistry;
        this.monitor = monitor;
        this.tags = Tags.of("application", applicationName != null ? applicationName : "unknown");
        this.lastMonitorInfo.set(new MonitorInfo());
    }
    
    /**
     * 初始化指标
     * 在Spring Bean创建后自动调用
     */
    public void bindTo() {
        try {
            // ========== 注册Gauge指标（当前值） ==========
            
            // 热Key数量
            hotKeyCountGauge = Gauge.builder("hotkey.monitor.hotkey.count", 
                    () -> getMonitorInfo().getHotKeyCount())
                    .description("当前热Key数量")
                    .tags(tags)
                    .register(meterRegistry);
            
            // 数据存储层大小
            storageSizeGauge = Gauge.builder("hotkey.monitor.storage.size", 
                    () -> getMonitorInfo().getStorageSize())
                    .description("数据存储层大小（key数量）")
                    .tags(tags)
                    .register(meterRegistry);
            
            // 访问记录模块数据量
            recorderSizeGauge = Gauge.builder("hotkey.monitor.recorder.size", 
                    () -> getMonitorInfo().getRecorderSize())
                    .description("访问记录模块数据量")
                    .tags(tags)
                    .register(meterRegistry);
            
            // 访问记录模块内存大小
            recorderMemorySizeGauge = Gauge.builder("hotkey.monitor.recorder.memory.size", 
                    () -> getMonitorInfo().getRecorderMemorySize())
                    .description("访问记录模块内存大小（字节）")
                    .tags(tags)
                    .register(meterRegistry);
            
            // 缓存数据更新器注册表数据量
            updaterSizeGauge = Gauge.builder("hotkey.monitor.updater.size", 
                    () -> getMonitorInfo().getUpdaterSize())
                    .description("缓存数据更新器注册表数据量")
                    .tags(tags)
                    .register(meterRegistry);
            
            // 缓存数据更新器注册表内存大小
            updaterMemorySizeGauge = Gauge.builder("hotkey.monitor.updater.memory.size", 
                    () -> getMonitorInfo().getUpdaterMemorySize())
                    .description("缓存数据更新器注册表内存大小（字节）")
                    .tags(tags)
                    .register(meterRegistry);
            
            // wrapGet的QPS
            wrapGetQpsGauge = Gauge.builder("hotkey.monitor.wrapget.qps", 
                    () -> getMonitorInfo().getWrapGetQps())
                    .description("wrapGet的QPS（每秒请求数）")
                    .tags(tags)
                    .register(meterRegistry);
            
            // 每秒访问的不同key数量
            keysPerSecondGauge = Gauge.builder("hotkey.monitor.keys.per.second", 
                    () -> getMonitorInfo().getKeysPerSecond())
                    .description("每秒访问的不同key数量")
                    .tags(tags)
                    .register(meterRegistry);
            
            // 热Key命中率
            hotKeyHitRateGauge = Gauge.builder("hotkey.monitor.hit.rate", 
                    () -> getMonitorInfo().getHotKeyHitRate())
                    .description("热Key缓存命中率（0.0-1.0）")
                    .tags(tags)
                    .register(meterRegistry);
            
            // 热Key流量占比
            hotKeyTrafficRatioGauge = Gauge.builder("hotkey.monitor.traffic.ratio", 
                    () -> getMonitorInfo().getHotKeyTrafficRatio())
                    .description("热Key流量占比（0.0-1.0）")
                    .tags(tags)
                    .register(meterRegistry);
            
            // ========== 注册Counter指标（累计值） ==========
            
            // wrapGet总调用次数
            totalWrapGetCounter = Counter.builder("hotkey.monitor.wrapget.total")
                    .description("wrapGet总调用次数（累计）")
                    .tags(tags)
                    .register(meterRegistry);
            
            // 热Key访问总次数
            hotKeyAccessCounter = Counter.builder("hotkey.monitor.hotkey.access.total")
                    .description("热Key访问总次数（累计）")
                    .tags(tags)
                    .register(meterRegistry);
            
            // 热Key缓存命中次数
            hotKeyHitCounter = Counter.builder("hotkey.monitor.hotkey.hit.total")
                    .description("热Key缓存命中次数（累计）")
                    .tags(tags)
                    .register(meterRegistry);
            
            // 热Key缓存未命中次数
            hotKeyMissCounter = Counter.builder("hotkey.monitor.hotkey.miss.total")
                    .description("热Key缓存未命中次数（累计）")
                    .tags(tags)
                    .register(meterRegistry);
            
            log.info("热Key监控Prometheus指标注册成功");
        } catch (Exception e) {
            log.error("注册Prometheus指标失败", e);
        }
    }
    
    /**
     * 更新Counter指标
     * 计算增量并更新Counter
     * 注意：Counter只能增加，不能减少，所以需要计算增量
     */
    public void updateCounters() {
        try {
            MonitorInfo currentInfo = getMonitorInfo();
            MonitorInfo lastInfo = lastMonitorInfo.get();
            
            // 计算增量
            long wrapGetDelta = currentInfo.getTotalWrapGetCount() - lastInfo.getTotalWrapGetCount();
            long accessDelta = currentInfo.getHotKeyAccessCount() - lastInfo.getHotKeyAccessCount();
            long hitDelta = currentInfo.getHotKeyHitCount() - lastInfo.getHotKeyHitCount();
            long missDelta = currentInfo.getHotKeyMissCount() - lastInfo.getHotKeyMissCount();
            
            // 更新Counter（只增加增量）
            if (wrapGetDelta > 0) {
                totalWrapGetCounter.increment(wrapGetDelta);
            }
            if (accessDelta > 0) {
                hotKeyAccessCounter.increment(accessDelta);
            }
            if (hitDelta > 0) {
                hotKeyHitCounter.increment(hitDelta);
            }
            if (missDelta > 0) {
                hotKeyMissCounter.increment(missDelta);
            }
            
            // 更新上次统计值
            lastMonitorInfo.set(currentInfo);
        } catch (Exception e) {
            log.error("更新Prometheus Counter指标失败", e);
        }
    }
    
    /**
     * 获取监控信息
     * 
     * @return 监控信息对象
     */
    private MonitorInfo getMonitorInfo() {
        if (monitor == null) {
            return new MonitorInfo();
        }
        try {
            return monitor.getMonitorInfo();
        } catch (Exception e) {
            log.error("获取监控信息失败", e);
            return new MonitorInfo();
        }
    }
}
