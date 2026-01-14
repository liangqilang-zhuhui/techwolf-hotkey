# Redis热Key检测与本地缓存Spring Boot Starter

## 简介

这是一个用于检测Redis热Key并提供本地缓存的Spring Boot Starter。当某些Redis key访问频率过高时，会自动将其缓存到本地Caffeine缓存中，减少对Redis的访问压力。

## 功能特性

- ✅ **自动热Key检测**：基于滑动窗口算法，自动检测访问频率高的Redis key
- ✅ **本地缓存**：使用Caffeine实现高性能本地缓存
- ✅ **自动淘汰**：支持时间过期、容量限制等多种淘汰策略
- ✅ **零侵入**：自动集成到RedisClientManager，无需修改业务代码
- ✅ **可配置**：支持丰富的配置参数，灵活适应不同场景

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
    window-size: 60                      # 统计窗口大小（秒）
    top-n: 10                            # Top N数量
    hot-key-qps-threshold: 3000.0        # 热Key QPS阈值（次/秒）
    warm-key-qps-threshold: 500.0        # 温Key QPS阈值（次/秒）
    promotion-interval: 5000             # 晋升间隔（毫秒）
    demotion-interval: 60000             # 降级间隔（毫秒）
  storage:
    enabled: true                        # 是否启用本地缓存
    maximum-size: 100                    # 最大缓存数量
    expire-after-write: 60               # 过期时间（秒）
  recorder:
    max-capacity: 10000                  # 访问记录最大容量
    window-size: 60                      # 统计窗口大小（秒）
  monitor:
    enabled: true                         # 是否启用监控
    interval: 60000                       # 监控输出间隔（毫秒）
```

### 3. 使用方式

#### 方式一：自动集成（推荐）

如果你的项目使用了 `RedisClientManager`，starter会自动注入 `HotKeyClient`，无需任何额外代码：

```java
@Autowired
private RedisClientManager redisClientManager;

// 直接使用，自动享受热Key缓存
String value = redisClientManager.get("user:123");
```

#### 方式二：直接使用HotKeyClient

```java
@Autowired
private IHotKeyClient hotKeyClient;

@Autowired
private StringRedisTemplate redisTemplate;

public String get(String key) {
    // 使用wrapGet方法，自动处理热Key缓存逻辑
    return hotKeyClient.wrapGet(key, () -> redisTemplate.opsForValue().get(key));
}
```

## 配置说明

### 检测配置（detection）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| window-size | int | 60 | 统计窗口大小（秒） |
| top-n | int | 10 | Top N数量 |
| hot-key-qps-threshold | double | 3000.0 | 热Key QPS阈值（次/秒） |
| warm-key-qps-threshold | double | 500.0 | 温Key QPS阈值（次/秒） |
| promotion-interval | long | 5000 | 晋升间隔（毫秒） |
| demotion-interval | long | 60000 | 降级间隔（毫秒） |
| max-stats-capacity | int | 5000 | 统计信息最大容量 |

### 存储配置（storage）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| enabled | boolean | true | 是否启用本地缓存 |
| maximum-size | long | 100 | 最大缓存数量 |
| expire-after-write | int | 60 | 过期时间（秒） |

### 访问记录配置（recorder）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| max-capacity | int | 10000 | 访问记录最大容量 |
| window-size | int | 60 | 统计窗口大小（秒） |

### 监控配置（monitor）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| enabled | boolean | true | 是否启用监控 |
| interval | long | 60000 | 监控输出间隔（毫秒） |

## 工作原理

1. **访问统计**：每次Redis get操作都会记录访问统计
2. **热Key检测**：每5秒计算一次Top N热Key（QPS >= 3000）
3. **本地缓存**：热Key的数据会自动缓存到本地Caffeine缓存
4. **自动淘汰**：每分钟检查一次，移除不再热门的key

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

1. **数据一致性**：本地缓存的数据可能与Redis中的数据存在短暂不一致（最多60秒）
2. **内存占用**：本地缓存会占用JVM内存，需要合理设置缓存容量
3. **适用场景**：适合读多写少、有明显热点数据的场景

## 模块说明

本项目采用多模块结构：

- `hotkey-core`：核心功能模块（不依赖Spring）
- `hotkey-spring-boot-autoconfigure`：Spring Boot自动配置模块
- `hotkey-spring-boot-starter`：Starter模块（薄包装）

## 版本历史

### v1.0.0

- ✅ 实现热Key检测机制（滑动窗口、TopN计算）
- ✅ 实现本地Caffeine缓存
- ✅ 实现Spring Boot Starter自动配置
- ✅ 实现自动集成到RedisClientManager
- ✅ 实现监控和日志

## 许可证

Copyright © 2024 TechWolf
