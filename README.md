# Redis热Key检测与本地缓存Spring Boot Starter

## 简介

这是一个用于检测Redis热Key并提供本地缓存的Spring Boot Starter。当某些Redis key访问频率过高时，会自动将其缓存到本地Caffeine缓存中，减少对Redis的访问压力。

## 功能特性

- ✅ **自动热Key检测**：基于滑动窗口算法，自动检测访问频率高的Redis key（QPS >= 3000）
- ✅ **本地缓存**：使用Caffeine实现高性能本地缓存，默认60秒过期
- ✅ **自动刷新**：定时刷新热Key数据，保证数据新鲜度（默认10秒刷新一次）
- ✅ **自动淘汰**：支持时间过期、容量限制等多种淘汰策略，自动移除不再热门的key
- ✅ **智能采样**：支持低频率key的采样机制，降低内存占用
- ✅ **零侵入**：通过HotKeyClient统一处理，无需修改业务代码
- ✅ **可配置**：支持丰富的配置参数，灵活适应不同场景
- ✅ **监控统计**：提供监控接口，实时查看热Key状态和系统指标

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>cn.techwolf.datastar</groupId>
    <artifactId>hotkey-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置参数

在 `application.yml` 中添加配置：

```yaml
hotkey:
  enabled: true                          # 是否启用热Key检测
  detection:
    window-size: 10                       # 统计窗口大小（秒），默认10秒
    top-n: 10                             # Top N数量，每5秒晋升Top N个热Key
    hot-key-qps-threshold: 3000.0          # 热Key QPS阈值（次/秒），QPS >= 3000才能成为热Key
    warm-key-qps-threshold: 500.0         # 温Key QPS阈值（次/秒），冷key需要QPS >= 500才能升级到温key
    promotion-interval: 5000               # 晋升间隔（毫秒），每5秒探测一次访问记录
    demotion-interval: 60000              # 降级间隔（毫秒），每分钟探测一次访问记录
    max-stats-capacity: 5000               # 统计信息最大容量，建议设置为topN的100-500倍
    admission-min-frequency: 10.0         # 准入最小访问频率阈值（次/秒），低于此频率的key使用采样机制
    sampling-rate: 0.1                    # 采样率（0.0-1.0），低频率key按此比例采样记录，默认10%
    fast-admission-threshold: 50.0        # 快速准入阈值（次/秒），超过此频率的key直接准入
    rejected-access-threshold: 10000       # 被拒绝访问强制准入阈值，被拒绝10000次后强制准入
    enable-consistent-sampling: true      # 是否启用一致性采样，使用key的hash值进行采样
    capacity-usage-threshold: 0.5          # 容量使用率阈值（0.0-1.0），低于此阈值时提高采样率
  storage:
    enabled: true                         # 是否启用本地缓存
    maximum-size: 200                      # 最大缓存数量，默认200
    expire-after-write: 60                # 写入后过期时间（秒），默认60秒
    record-stats: true                     # 是否记录统计信息
  recorder:
    max-capacity: 100000                   # 访问记录最大容量，默认100000
    window-size: 10                       # 统计窗口大小（秒），用于计算QPS，默认10秒
  refresh:
    enabled: true                          # 是否启用自动刷新，默认true
    interval: 10000                        # 刷新间隔（毫秒），默认10秒刷新一次
    max-failure-count: 3                  # 刷新失败重试次数，连续失败超过此次数后移除该热key
  monitor:
    enabled: true                          # 是否启用监控，默认true
    interval: 60000                        # 监控输出间隔（毫秒），默认60秒
```

### 3. 使用方式

#### 方式一：直接使用HotKeyClient

```java
@Autowired
private IHotKeyClient hotKeyClient;

@Autowired
private StringRedisTemplate redisTemplate;

public String get(String key) {
    // 使用wrapGet方法，自动处理热Key缓存逻辑
    // 参数1：Redis key
    // 参数2：数据获取回调函数（Function<String, String>），用于从Redis获取数据
    return hotKeyClient.wrapGet(key, k -> redisTemplate.opsForValue().get(k));
}

// 或者使用Lambda表达式
public String get(String key) {
    return hotKeyClient.wrapGet(key, redisTemplate.opsForValue()::get);
}
```

#### 方式二：非Spring环境使用

```java
// 创建配置
HotKeyConfig config = new HotKeyConfig();
config.setEnabled(true);
config.getDetection().setHotKeyQpsThreshold(3000.0);
config.getDetection().setTopN(10);
// ... 其他配置

// 创建客户端
HotKeyClient client = new HotKeyClient(config);

// 使用
String value = client.wrapGet("user:123", k -> redisTemplate.opsForValue().get(k));

// 关闭客户端（释放资源）
client.shutdown();
```

## 配置说明

### 检测配置（detection）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| window-size | int | 10 | 统计窗口大小（秒），用于计算QPS |
| top-n | int | 10 | Top N数量，每5秒晋升Top N个热Key |
| hot-key-qps-threshold | double | 3000.0 | 热Key QPS阈值（次/秒），只有QPS >= 3000的key才能成为热Key |
| warm-key-qps-threshold | double | 500.0 | 温Key QPS阈值（次/秒），冷key需要QPS >= 500才能升级到温key |
| promotion-interval | long | 5000 | 晋升间隔（毫秒），每5秒探测一次访问记录 |
| demotion-interval | long | 60000 | 降级间隔（毫秒），每分钟探测一次访问记录 |
| max-stats-capacity | int | 5000 | 统计信息最大容量，建议设置为topN的100-500倍 |
| admission-min-frequency | double | 10.0 | 准入最小访问频率阈值（次/秒），低于此频率的key使用采样机制 |
| sampling-rate | double | 0.1 | 采样率（0.0-1.0），低频率key按此比例采样记录，默认10% |
| fast-admission-threshold | double | 50.0 | 快速准入阈值（次/秒），超过此频率的key直接准入，不进行采样 |
| rejected-access-threshold | int | 10000 | 被拒绝访问强制准入阈值，被拒绝10000次后强制准入 |
| enable-consistent-sampling | boolean | true | 是否启用一致性采样，使用key的hash值进行采样 |
| capacity-usage-threshold | double | 0.5 | 容量使用率阈值（0.0-1.0），低于此阈值时提高采样率 |

### 存储配置（storage）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| enabled | boolean | true | 是否启用本地缓存 |
| maximum-size | long | 200 | 最大缓存数量 |
| expire-after-write | int | 60 | 写入后过期时间（秒） |
| record-stats | boolean | true | 是否记录统计信息 |

### 访问记录配置（recorder）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| max-capacity | int | 100000 | 访问记录最大容量，超限时自动清理低QPS的key |
| window-size | int | 10 | 统计窗口大小（秒），用于计算QPS |

### 刷新配置（refresh）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| enabled | boolean | true | 是否启用自动刷新 |
| interval | long | 10000 | 刷新间隔（毫秒），默认10秒刷新一次热Key数据 |
| max-failure-count | int | 3 | 刷新失败重试次数，连续失败超过此次数后移除该热key |

### 监控配置（monitor）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| enabled | boolean | true | 是否启用监控 |
| interval | long | 60000 | 监控输出间隔（毫秒），默认60秒输出一次 |

## JMX 监控

系统提供了 JMX MBean 来暴露监控数据，支持通过 JConsole、VisualVM 等工具查看实时监控信息。

### MBean 信息

- **MBean 名称**：`cn.techwolf.datastar.hotkey:type=HotKeyMonitor`
- **自动注册**：当监控功能启用时，MBean 会自动注册到 MBeanServer

### 可用的监控指标

| 属性/方法 | 类型 | 说明 |
|-----------|------|------|
| `getHotKeyCount()` | int | 当前热Key数量 |
| `getHotKeys()` | String | 热Key列表（JSON格式） |
| `getStorageSize()` | long | 数据存储层大小 |
| `getRecorderSize()` | int | 访问记录模块的数据量 |
| `getRecorderMemorySize()` | long | 访问记录模块的内存大小（字节） |
| `getUpdaterSize()` | int | 缓存数据更新器注册表的数据量 |
| `getUpdaterMemorySize()` | long | 缓存数据更新器注册表的内存大小（字节） |
| `getMonitorInfoJson()` | String | 完整的监控信息（JSON格式） |
| `refresh()` | void | 手动刷新监控数据 |

### 使用方式

#### 1. 通过 JConsole 查看

1. 启动应用后，运行 `jconsole` 命令
2. 连接到本地进程或远程进程
3. 在 MBeans 标签页中找到 `cn.techwolf.datastar.hotkey` -> `HotKeyMonitor`
4. 查看 Attributes 和 Operations

#### 2. 通过 VisualVM 查看

1. 启动应用后，运行 `jvisualvm` 命令
2. 连接到本地进程或远程进程
3. 在 MBeans 标签页中找到 `cn.techwolf.datastar.hotkey` -> `HotKeyMonitor`
4. 查看属性和操作

#### 3. 通过代码访问

```java
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
ObjectName objectName = new ObjectName("cn.techwolf.datastar.hotkey:type=HotKeyMonitor");
HotKeyMonitorMXBean mBean = JMX.newMBeanProxy(mBeanServer, objectName, HotKeyMonitorMXBean.class);

// 获取热Key数量
int count = mBean.getHotKeyCount();

// 获取完整监控信息
String json = mBean.getMonitorInfoJson();
```

### 监控数据结构

监控数据通过 `MonitorInfo` 类进行结构化存储，包含以下字段：

- `hotKeys`: 热Key列表（Set<String>）
- `hotKeyCount`: 热Key数量
- `storageSize`: 数据存储层大小
- `recorderSize`: 访问记录模块的数据量
- `recorderMemorySize`: 访问记录模块的内存大小（估算，单位：字节）
- `updaterSize`: 缓存数据更新器注册表的数据量
- `updaterMemorySize`: 缓存数据更新器注册表的内存大小（估算，单位：字节）

## 工作原理

### 核心流程

1. **访问统计**：每次调用 `wrapGet` 方法时，异步记录访问统计（使用滑动窗口算法计算QPS）
2. **热Key检测**：
   - 每5秒执行一次热Key晋升：从满足QPS阈值（>= 3000）的key中，按QPS降序排序，取Top N个晋升为热Key
   - 每60秒执行一次热Key降级：移除QPS低于阈值（< 3000）的key
3. **本地缓存**：
   - 当key被识别为热Key后，自动缓存到本地Caffeine缓存
   - 缓存命中时直接返回，无需访问Redis
   - 缓存未命中时从Redis获取，并更新本地缓存
4. **自动刷新**：每10秒自动刷新一次热Key数据，保证数据新鲜度
5. **自动淘汰**：
   - 时间过期：缓存数据60秒后自动过期
   - 容量限制：超过最大容量时，按LRU策略淘汰
   - 降级移除：key不再热门时，自动从缓存中移除

### 模块架构

系统采用6个核心模块的架构设计：

- **模块一：热Key管理器（Manager）**：维护和管理热Key列表，判断是否热key
- **模块二：数据存储（Storage）**：使用Caffeine存储热key的value
- **模块三：访问记录（Recorder）**：记录所有key的访问统计，计算QPS
- **模块四：选择器（Selector）**：根据访问统计选择哪些key应该晋升或降级
- **模块五：监控器（Monitor）**：监控热key列表、数据存储层大小、访问记录模块数据量
- **模块六：缓存数据更新器（Updater）**：存储数据获取回调函数，定时刷新热Key数据

### 智能采样机制

为了降低内存占用，系统实现了智能采样机制：

- **低频率key采样**：QPS < 10的key按10%的比例采样记录
- **快速准入**：QPS >= 50的key直接准入，不进行采样
- **强制准入**：被采样拒绝10000次后强制准入，避免高频率key因采样被拒绝
- **一致性采样**：使用key的hash值进行采样，保证同一个key的采样结果一致
- **动态调整**：容量使用率低于50%时，采样率提高2倍

## 监控信息

监控信息会定期输出到日志（默认每60秒）：

```
========== 热Key监控统计 ==========
热Key数量: 5
热Key列表: [user:123, user:456, ...]
数据存储层大小: 5
访问记录模块数据量: 100
访问记录模块内存大小(估算): 10000 bytes
====================================
```

也可以通过代码获取监控信息：

```java
@Autowired
private IHotKeyMonitor hotKeyMonitor;

HotKeyMonitor.MonitorInfo info = hotKeyMonitor.getMonitorInfo();
System.out.println("热Key数量: " + info.getHotKeyCount());
```

## 注意事项

1. **数据一致性**：
   - 本地缓存的数据可能与Redis中的数据存在短暂不一致（最多60秒）
   - 自动刷新机制每10秒刷新一次，可以缩短不一致时间窗口
   - 适合读多写少的场景，对数据一致性要求极高的场景请谨慎使用

2. **内存占用**：
   - 本地缓存会占用JVM内存，需要合理设置缓存容量（`storage.maximum-size`）
   - 访问记录也会占用内存，建议根据实际情况设置 `recorder.max-capacity`
   - 系统会自动清理低QPS的key，保留80%的容量

3. **适用场景**：
   - ✅ 读多写少、有明显热点数据的场景
   - ✅ 对数据一致性要求不是特别严格的场景
   - ✅ Redis访问压力大的场景
   - ❌ 对数据一致性要求极高的场景
   - ❌ 写多读少的场景

4. **性能优化**：
   - 访问统计采用异步记录，不阻塞主流程
   - 使用滑动窗口算法，内存占用可控
   - 智能采样机制，降低低频率key的内存占用
   - 自动清理机制，防止内存溢出

5. **资源释放**：
   - 在非Spring环境使用或应用关闭时，记得调用 `client.shutdown()` 释放资源
   - Spring环境会自动管理Bean的生命周期

## 模块说明

本项目采用多模块结构：

- `hotkey-core`：核心功能模块（不依赖Spring）
- `hotkey-spring-boot-autoconfigure`：Spring Boot自动配置模块
- `hotkey-spring-boot-starter`：Starter模块（薄包装）

## 版本历史

### v1.0.0

- ✅ 实现热Key检测机制（滑动窗口、TopN计算）
- ✅ 实现本地Caffeine缓存（60秒过期，最大200个key）
- ✅ 实现自动刷新机制（每10秒刷新一次热Key数据）
- ✅ 实现智能采样机制（降低低频率key的内存占用）
- ✅ 实现自动淘汰机制（时间过期、容量限制、降级移除）
- ✅ 实现Spring Boot Starter自动配置
- ✅ 实现监控和日志（每60秒输出监控信息）
- ✅ 实现6个核心模块的架构设计（Manager、Storage、Recorder、Selector、Monitor、Updater）

## 许可证

Copyright © 2024 TechWolf
