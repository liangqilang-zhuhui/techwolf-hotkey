# Prometheus 集成使用指南

## 概述

热Key监控模块已集成 Prometheus 指标支持，通过 Micrometer 将监控数据暴露为 Prometheus 指标，方便使用 Prometheus 和 Grafana 进行监控和可视化。

## 功能特性

- ✅ **可选集成**：使用 `@ConditionalOnClass`，如果项目没有 Micrometer，不影响运行
- ✅ **复用数据源**：直接使用现有的 `MonitorInfo`，避免重复采集
- ✅ **符合规范**：使用 Gauge 和 Counter 两种指标类型，符合 Prometheus 最佳实践
- ✅ **低性能开销**：Gauge 按需计算，Counter 按增量更新
- ✅ **易于扩展**：可以轻松添加新的监控指标

## 依赖配置

### 1. 添加 Micrometer 依赖

如果您的项目已经集成了 Spring Boot Actuator 和 Micrometer，则无需额外配置。如果没有，请在 `pom.xml` 中添加：

```xml
<!-- Spring Boot Actuator -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer Prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### 2. 配置 Actuator

在 `application.yml` 中配置 Actuator 端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info
  metrics:
    export:
      prometheus:
        enabled: true
```

### 3. 配置热Key监控

在 `application.yml` 中配置热Key监控（Prometheus 默认启用）：

```yaml
hotkey:
  enabled: true
  monitor:
    enabled: true
    interval: 60000  # 监控间隔（毫秒）
    prometheus:
      enabled: true   # 启用Prometheus指标（默认启用）
```

## 暴露的指标

### Gauge 指标（当前值）

| 指标名称 | 描述 | 单位 |
|---------|------|------|
| `hotkey.monitor.hotkey.count` | 当前热Key数量 | 个 |
| `hotkey.monitor.storage.size` | 数据存储层大小 | key数量 |
| `hotkey.monitor.recorder.size` | 访问记录模块数据量 | 个 |
| `hotkey.monitor.recorder.memory.size` | 访问记录模块内存大小 | 字节 |
| `hotkey.monitor.updater.size` | 缓存数据更新器注册表数据量 | 个 |
| `hotkey.monitor.updater.memory.size` | 缓存数据更新器注册表内存大小 | 字节 |
| `hotkey.monitor.wrapget.qps` | wrapGet的QPS | 请求/秒 |
| `hotkey.monitor.keys.per.second` | 每秒访问的不同key数量 | key/秒 |
| `hotkey.monitor.hit.rate` | 热Key缓存命中率 | 0.0-1.0 |
| `hotkey.monitor.traffic.ratio` | 热Key流量占比 | 0.0-1.0 |

### Counter 指标（累计值）

| 指标名称 | 描述 | 单位 |
|---------|------|------|
| `hotkey.monitor.wrapget.total` | wrapGet总调用次数 | 次 |
| `hotkey.monitor.hotkey.access.total` | 热Key访问总次数 | 次 |
| `hotkey.monitor.hotkey.hit.total` | 热Key缓存命中次数 | 次 |
| `hotkey.monitor.hotkey.miss.total` | 热Key缓存未命中次数 | 次 |

## 使用示例

### 1. 访问 Prometheus 指标

启动应用后，访问以下端点获取 Prometheus 格式的指标：

```
http://localhost:8080/actuator/prometheus
```

### 2. Prometheus 配置

在 `prometheus.yml` 中添加抓取配置：

```yaml
scrape_configs:
  - job_name: 'hotkey-monitor'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

### 3. Grafana 仪表板

可以基于以下指标创建 Grafana 仪表板：

#### 热Key数量趋势
```promql
hotkey_monitor_hotkey_count
```

#### QPS 趋势
```promql
hotkey_monitor_wrapget_qps
```

#### 命中率趋势
```promql
hotkey_monitor_hit_rate
```

#### 流量占比趋势
```promql
hotkey_monitor_traffic_ratio
```

#### 内存使用情况
```promql
hotkey_monitor_recorder_memory_size
hotkey_monitor_updater_memory_size
```

## 架构设计

### 类结构

```
HotKeyMetrics
├── MeterRegistry (Micrometer核心接口)
├── IHotKeyMonitor (监控器接口)
└── Tags (指标标签)
```

### 工作流程

1. **初始化阶段**：
   - Spring Boot 启动时，`HotKeyAutoConfiguration` 检测到 `MeterRegistry` 存在
   - 创建 `HotKeyMetrics` 实例并注册所有指标

2. **数据采集阶段**：
   - Gauge 指标：每次 Prometheus 抓取时自动调用 lambda 函数获取当前值
   - Counter 指标：通过定时任务（每分钟）计算增量并更新

3. **指标暴露阶段**：
   - 通过 Spring Boot Actuator 的 `/actuator/prometheus` 端点暴露指标
   - Prometheus 定期抓取指标数据

## 注意事项

1. **可选依赖**：如果项目没有 Micrometer，Prometheus 指标功能不会启用，但不影响其他功能
2. **性能影响**：Gauge 指标按需计算，Counter 指标每分钟更新一次，性能开销很小
3. **指标标签**：所有指标都包含 `application` 标签，用于区分不同应用实例
4. **配置优先级**：可以通过 `hotkey.monitor.prometheus.enabled=false` 禁用 Prometheus 指标

## 故障排查

### 问题1：指标未暴露

**原因**：可能没有添加 Micrometer 依赖或未启用 Actuator 端点

**解决**：
1. 检查 `pom.xml` 是否包含 `micrometer-registry-prometheus` 依赖
2. 检查 `application.yml` 中是否配置了 `management.endpoints.web.exposure.include=prometheus`

### 问题2：指标值为 0

**原因**：可能是监控器未初始化或未启用

**解决**：
1. 检查 `hotkey.enabled=true`
2. 检查 `hotkey.monitor.enabled=true`
3. 检查日志中是否有 "热Key监控Prometheus指标注册成功" 的日志

### 问题3：编译错误

**原因**：可能是 `hotkey-core` 模块中引用了 Micrometer 类

**解决**：确保 `HotKeyMetrics` 类在 `hotkey-spring-boot-autoconfigure` 模块中，而不是 `hotkey-core` 模块中

## 扩展开发

如果需要添加新的监控指标，可以：

1. 在 `MonitorInfo` 中添加新的字段
2. 在 `HotKeyMonitor.getMonitorInfo()` 中采集数据
3. 在 `HotKeyMetrics.bindTo()` 中注册新指标

示例：

```java
// 在 HotKeyMetrics 中添加新指标
private Gauge newMetricGauge;

// 在 bindTo() 中注册
newMetricGauge = Gauge.builder("hotkey.monitor.new.metric", 
        () -> getMonitorInfo().getNewMetric())
        .description("新指标描述")
        .tags(tags)
        .register(meterRegistry);
```

## 参考文档

- [Micrometer 官方文档](https://micrometer.io/docs)
- [Prometheus 官方文档](https://prometheus.io/docs/)
- [Spring Boot Actuator 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
