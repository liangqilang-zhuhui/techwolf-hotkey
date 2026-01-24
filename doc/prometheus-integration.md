# Prometheus 集成使用指南

## 概述

热Key监控模块已集成 Prometheus 指标支持，通过 Micrometer 将监控数据暴露为 Prometheus 指标，方便使用 Prometheus 和 Grafana 进行监控和可视化。

## 功能特性

- ✅ **可选集成**：使用 `@ConditionalOnClass`，如果项目没有 Micrometer，不影响运行
- ✅ **复用数据源**：直接使用现有的 `MonitorInfo`，避免重复采集
- ✅ **符合规范**：使用 Gauge 指标类型，符合 Prometheus 最佳实践
- ✅ **低性能开销**：Gauge 按需计算，每次 Prometheus 抓取时自动获取当前值
- ✅ **易于扩展**：可以轻松添加新的监控指标
- ✅ **默认启用**：Prometheus 指标默认启用，无需额外配置

## 快速开始

### 1. 添加依赖

在 `pom.xml` 中添加以下依赖：

```xml
<!-- Micrometer Core (可选，如果项目已有则不需要) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
</dependency>

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

**注意**：
- 如果您的项目已经集成了 Spring Boot Actuator 和 Micrometer，则无需额外配置
- `micrometer-core` 是可选依赖，如果项目已有 Micrometer 相关依赖则不需要

### 2. 配置 Actuator 和 Prometheus

在 `application.yml` 中配置 Actuator 端点：

```yaml
management:
  endpoints:
    web:
      base-path: /actuator  # 明确指定基础路径
      exposure:
        include: prometheus,health,info,metrics  # 暴露 prometheus 端点
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true  # 启用 Prometheus 导出
    tags:
      application: ${spring.application.name:hotkey-demo}  # 应用名称标签
```

### 3. 配置热Key监控

在 `application.yml` 中配置热Key监控（Prometheus 默认启用）：

```yaml
hotkey:
  enabled: true                          # 是否启用，默认true
  monitor:
    enabled: true                         # 是否启用监控，默认true
    interval: 30000                      # 监控输出间隔（毫秒），默认60000
    prometheus:
      enabled: true                       # 启用Prometheus指标（默认启用）
```

**说明**：
- `hotkey.monitor.prometheus.enabled` 默认为 `true`，无需显式配置即可启用
- 如需禁用 Prometheus 指标，可设置 `hotkey.monitor.prometheus.enabled=false`

### 4. 验证配置

启动应用后，访问以下端点查看 Prometheus 指标：

```
http://localhost:8080/actuator/prometheus
```

如果看到以 `hotkey_` 开头的指标，说明配置成功。

## 暴露的指标

热Key监控模块共注册 **14个 Gauge 指标**，所有指标都包含 `application` 标签用于区分不同应用实例。

### 基础指标

| 指标名称 | 描述 | 单位 | 标签 |
|---------|------|------|------|
| `hotkey_count` | 当前热Key数量 | 个 | application |
| `hotkey_storage_size` | 本地缓存存储大小 | key数量 | application |

### 记录器指标

| 指标名称 | 描述 | 单位 | 标签 |
|---------|------|------|------|
| `hotkey_recorder_size` | 访问记录器大小 | 个 | application |
| `hotkey_recorder_memory_size` | 访问记录器内存大小 | 字节 | application |

### 更新器指标

| 指标名称 | 描述 | 单位 | 标签 |
|---------|------|------|------|
| `hotkey_updater_size` | 更新器大小 | 个 | application |
| `hotkey_updater_memory_size` | 更新器内存大小 | 字节 | application |

### 访问统计指标

| 指标名称 | 描述 | 单位 | 标签 |
|---------|------|------|------|
| `hotkey_total_access_count` | 总访问次数（wrapGet调用次数） | 次 | application |
| `hotkey_qps` | 当前QPS（每秒访问次数） | 请求/秒 | application |
| `hotkey_keys_per_second` | 每秒访问的Key数量 | key/秒 | application |

### 热Key统计指标

| 指标名称 | 描述 | 单位 | 标签 |
|---------|------|------|------|
| `hotkey_hot_access_count` | 热Key访问次数 | 次 | application |
| `hotkey_hot_hit_count` | 热Key缓存命中次数 | 次 | application |
| `hotkey_hot_miss_count` | 热Key缓存未命中次数 | 次 | application |
| `hotkey_hit_rate` | 热Key缓存命中率 | 0.0-1.0 | application |
| `hotkey_traffic_ratio` | 热Key流量占比 | 0.0-1.0 | application |

**注意**：Prometheus 会将指标名称中的点（`.`）替换为下划线（`_`），例如 `hotkey.count` 在 Prometheus 中显示为 `hotkey_count`。

## 使用示例

### 1. 访问 Prometheus 指标

启动应用后，访问以下端点获取 Prometheus 格式的指标：

```bash
curl http://localhost:8080/actuator/prometheus | grep hotkey
```

示例输出：
```
# HELP hotkey_count 当前热Key数量
# TYPE hotkey_count gauge
hotkey_count{application="hotkey-demo"} 8.0

# HELP hotkey_qps 当前QPS（每秒访问次数）
# TYPE hotkey_qps gauge
hotkey_qps{application="hotkey-demo"} 0.56

# HELP hotkey_hit_rate 热Key缓存命中率（0-1之间）
# TYPE hotkey_hit_rate gauge
hotkey_hit_rate{application="hotkey-demo"} 0.9765
```

### 2. Prometheus 配置

在 `prometheus.yml` 中添加抓取配置：

```yaml
scrape_configs:
  - job_name: 'hotkey-monitor'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s  # 抓取间隔
    static_configs:
      - targets: ['localhost:8080']
        labels:
          instance: 'hotkey-demo'
          environment: 'production'
```

### 3. Grafana 仪表板查询示例

可以基于以下 PromQL 查询创建 Grafana 仪表板：

#### 热Key数量趋势
```promql
hotkey_count{application="hotkey-demo"}
```

#### QPS 趋势
```promql
hotkey_qps{application="hotkey-demo"}
```

#### 命中率趋势（百分比）
```promql
hotkey_hit_rate{application="hotkey-demo"} * 100
```

#### 流量占比趋势（百分比）
```promql
hotkey_traffic_ratio{application="hotkey-demo"} * 100
```

#### 内存使用情况
```promql
# 访问记录器内存使用（MB）
hotkey_recorder_memory_size{application="hotkey-demo"} / 1024 / 1024

# 更新器内存使用（MB）
hotkey_updater_memory_size{application="hotkey-demo"} / 1024 / 1024
```

#### 热Key访问统计
```promql
# 热Key访问总次数
hotkey_hot_access_count{application="hotkey-demo"}

# 热Key缓存命中次数
hotkey_hot_hit_count{application="hotkey-demo"}

# 热Key缓存未命中次数
hotkey_hot_miss_count{application="hotkey-demo"}
```

#### 总访问统计
```promql
# 总访问次数
hotkey_total_access_count{application="hotkey-demo"}

# 每秒访问的Key数量
hotkey_keys_per_second{application="hotkey-demo"}
```

### 4. Grafana 仪表板配置示例

#### 面板1：热Key数量
- **查询**：`hotkey_count{application="hotkey-demo"}`
- **可视化类型**：Graph
- **Y轴标签**：热Key数量
- **图例**：热Key数量

#### 面板2：QPS监控
- **查询**：`hotkey_qps{application="hotkey-demo"}`
- **可视化类型**：Graph
- **Y轴标签**：QPS
- **图例**：当前QPS

#### 面板3：缓存命中率
- **查询**：`hotkey_hit_rate{application="hotkey-demo"} * 100`
- **可视化类型**：Graph
- **Y轴标签**：命中率（%）
- **图例**：缓存命中率
- **阈值**：绿色 > 90%，黄色 70-90%，红色 < 70%

#### 面板4：热Key流量占比
- **查询**：`hotkey_traffic_ratio{application="hotkey-demo"} * 100`
- **可视化类型**：Graph
- **Y轴标签**：流量占比（%）
- **图例**：热Key流量占比

## 架构设计

### 类结构

```
HotKeyMetricsConfig
├── MeterRegistry (Micrometer核心接口)
├── IHotKeyClient (热Key客户端接口)
├── IHotKeyMonitor (监控器接口)
└── MonitorInfo (监控信息数据对象)
```

### 工作流程

1. **初始化阶段**：
   - Spring Boot 启动时，`HotKeyMetricsConfig` 检测到 `MeterRegistry` 存在
   - 应用启动完成后（`ApplicationReadyEvent`），自动注册所有指标
   - 共注册 14 个 Gauge 指标

2. **数据采集阶段**：
   - Gauge 指标：每次 Prometheus 抓取时自动调用 lambda 函数获取当前值
   - 通过 `IHotKeyMonitor.getMonitorInfo()` 获取实时监控数据
   - 如果获取失败，返回 0 值，不会影响系统运行

3. **指标暴露阶段**：
   - 通过 Spring Boot Actuator 的 `/actuator/prometheus` 端点暴露指标
   - Prometheus 定期抓取指标数据（默认每15秒）

### 启用条件

Prometheus 指标功能在以下条件**全部满足**时才会启用：

1. ✅ 项目中存在 `MeterRegistry` 类（通过 `@ConditionalOnClass` 检测）
2. ✅ `hotkey.enabled=true`（默认启用）
3. ✅ `hotkey.monitor.prometheus.enabled=true`（默认启用）
4. ✅ `IHotKeyClient` 已初始化且启用
5. ✅ `IHotKeyMonitor` 已初始化

如果以上任一条件不满足，指标功能不会启用，但不会影响其他功能。

## 注意事项

1. **可选依赖**：如果项目没有 Micrometer，Prometheus 指标功能不会启用，但不影响其他功能
2. **性能影响**：Gauge 指标按需计算，每次 Prometheus 抓取时自动获取当前值，性能开销很小
3. **指标标签**：所有指标都包含 `application` 标签，用于区分不同应用实例
4. **配置优先级**：可以通过 `hotkey.monitor.prometheus.enabled=false` 禁用 Prometheus 指标
5. **默认启用**：Prometheus 指标默认启用，无需额外配置即可使用
6. **指标数量**：共注册 14 个 Gauge 指标，覆盖热Key监控的所有关键指标

## 故障排查

### 问题1：指标未暴露

**症状**：访问 `/actuator/prometheus` 端点时看不到 `hotkey_` 开头的指标

**可能原因**：
1. 未添加 Micrometer 依赖
2. 未启用 Actuator 端点
3. 热Key功能未启用
4. Prometheus 指标被禁用

**解决步骤**：
1. 检查 `pom.xml` 是否包含以下依赖：
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-actuator</artifactId>
   </dependency>
   <dependency>
       <groupId>io.micrometer</groupId>
       <artifactId>micrometer-registry-prometheus</artifactId>
   </dependency>
   ```

2. 检查 `application.yml` 中是否配置了：
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: prometheus
   ```

3. 检查 `hotkey.enabled=true` 和 `hotkey.monitor.prometheus.enabled=true`

4. 查看应用日志，确认是否有 "HotKey Prometheus指标注册成功，共注册14个指标" 的日志

### 问题2：指标值为 0

**症状**：能看到指标，但所有值都是 0

**可能原因**：
1. 监控器未初始化
2. 热Key功能未启用
3. 还没有访问过任何key

**解决步骤**：
1. 检查 `hotkey.enabled=true`
2. 检查 `hotkey.monitor.enabled=true`
3. 进行一些 Redis 访问操作，触发热Key检测
4. 等待一段时间后再次查看指标

### 问题3：指标名称不匹配

**症状**：在 Prometheus 中找不到预期的指标名称

**说明**：
- 代码中注册的指标名称使用点（`.`）分隔，如 `hotkey.count`
- Prometheus 会自动将点替换为下划线（`_`），如 `hotkey_count`
- 这是 Prometheus 的标准行为，不是问题

**正确使用**：
- 在 PromQL 查询中使用：`hotkey_count`
- 在代码中注册时使用：`hotkey.count`

### 问题4：编译错误

**症状**：编译时提示找不到 `MeterRegistry` 类

**可能原因**：未添加 Micrometer 依赖

**解决**：
1. 添加 `micrometer-core` 依赖（如果项目中没有其他 Micrometer 依赖）
2. 或添加 `spring-boot-starter-actuator` 依赖（会自动引入 Micrometer）

### 问题5：指标注册失败

**症状**：日志中看到 "跳过HotKey指标注册" 的警告

**可能原因**：
1. `MeterRegistry` 未找到
2. `HotKey客户端` 未找到
3. HotKey客户端未启用
4. HotKey监控器未初始化

**解决步骤**：
1. 检查日志中的具体警告信息
2. 确保热Key功能已正确启用
3. 确保应用已完全启动（指标注册在 `ApplicationReadyEvent` 时执行）

## 扩展开发

如果需要添加新的监控指标，可以按照以下步骤：

### 步骤1：在 MonitorInfo 中添加新字段

在 `MonitorInfo` 类中添加新的字段：

```java
public class MonitorInfo {
    // ... 现有字段 ...
    
    /**
     * 新指标字段
     */
    private double newMetric;
    
    // getter/setter
    public double getNewMetric() {
        return newMetric;
    }
    
    public void setNewMetric(double newMetric) {
        this.newMetric = newMetric;
    }
}
```

### 步骤2：在 HotKeyMonitor 中采集数据

在 `HotKeyMonitor.getMonitorInfo()` 方法中采集新指标的数据：

```java
@Override
public MonitorInfo getMonitorInfo() {
    MonitorInfo info = new MonitorInfo();
    // ... 现有数据采集 ...
    
    // 采集新指标数据
    info.setNewMetric(calculateNewMetric());
    
    return info;
}
```

### 步骤3：在 HotKeyMetricsConfig 中注册新指标

在 `HotKeyMetricsConfig.registerAllMetrics()` 方法中注册新指标：

```java
private void registerAllMetrics(IHotKeyMonitor monitor, List<Tag> tags) {
    // ... 现有指标注册 ...
    
    // 注册新指标
    registerGauge("hotkey.new.metric", "新指标描述", monitor, 
                  MonitorInfo::getNewMetric, tags);
}
```

### 完整示例

假设要添加一个"平均响应时间"指标：

```java
// 1. 在 MonitorInfo 中添加字段
public class MonitorInfo {
    private double avgResponseTime; // 平均响应时间（毫秒）
    // ... getter/setter
}

// 2. 在 HotKeyMonitor 中采集数据
@Override
public MonitorInfo getMonitorInfo() {
    MonitorInfo info = new MonitorInfo();
    // ... 其他数据采集 ...
    info.setAvgResponseTime(calculateAvgResponseTime());
    return info;
}

// 3. 在 HotKeyMetricsConfig 中注册指标
private void registerAllMetrics(IHotKeyMonitor monitor, List<Tag> tags) {
    // ... 现有指标 ...
    registerGauge("hotkey.avg.response.time", "平均响应时间（毫秒）", 
                  monitor, MonitorInfo::getAvgResponseTime, tags);
}
```

### 注意事项

1. **指标命名规范**：使用点（`.`）分隔，Prometheus 会自动转换为下划线（`_`）
2. **指标类型**：目前只支持 Gauge 类型，如需 Counter 类型需要修改代码
3. **性能考虑**：Gauge 的 lambda 函数会在每次 Prometheus 抓取时执行，确保计算效率
4. **异常处理**：`getMonitorInfo()` 方法中已包含异常处理，返回 0 值

## 完整配置示例

以下是一个完整的 `application.yml` 配置示例：

```yaml
server:
  port: 8080

spring:
  application:
    name: hotkey-demo
  redis:
    host: 127.0.0.1
    port: 6379

# 热Key配置
hotkey:
  enabled: true
  detection:
    hot-key-qps-threshold: 30.0
    warm-key-qps-threshold: 10.0
    promotion-interval: 5000
    demotion-interval: 60000
  storage:
    enabled: true
    maximum-size: 100
    expire-after-write: 60
  recorder:
    max-capacity: 15
    window-size: 60
  monitor:
    enabled: true
    interval: 30000
    prometheus:
      enabled: true  # 启用Prometheus指标（默认启用）

# Actuator配置
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: prometheus,health,info,metrics
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name:hotkey-demo}
```

## 参考文档

- [Micrometer 官方文档](https://micrometer.io/docs)
- [Prometheus 官方文档](https://prometheus.io/docs/)
- [Spring Boot Actuator 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [PromQL 查询语言](https://prometheus.io/docs/prometheus/latest/querying/basics/)