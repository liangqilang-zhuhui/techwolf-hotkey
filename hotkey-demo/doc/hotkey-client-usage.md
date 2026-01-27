# HotKeyClient 使用指南

## 概述

`HotKeyClient` 是热Key缓存服务的客户端，提供简单易用的API，不依赖Spring框架，可以直接通过 `new` 对象使用。

## 快速开始

### 1. 创建配置对象

```java
import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.HotKeyClient;
import cn.techwolf.datastar.hotkey.IHotKeyClient;

// 创建配置对象
HotKeyConfig config = new HotKeyConfig();
config.setEnabled(true);

// 配置检测参数
HotKeyConfig.Detection detection = config.getDetection();
detection.setWindowSize(60);                    // 统计窗口大小（秒）
detection.setTopN(10);                          // Top N数量
detection.setHotKeyQpsThreshold(3000.0);        // 热Key QPS阈值
detection.setWarmKeyQpsThreshold(500.0);        // 温Key QPS阈值
detection.setPromotionInterval(5000);             // 晋升间隔（毫秒）
detection.setDemotionInterval(60000);           // 降级间隔（毫秒）

// 配置存储参数
HotKeyConfig.Storage storage = config.getStorage();
storage.setEnabled(true);
storage.setMaximumSize(100);                    // 最大缓存数量
storage.setExpireAfterWrite(60);                // 过期时间（秒）

// 配置记录器参数
HotKeyConfig.Recorder recorder = config.getRecorder();
recorder.setMaxCapacity(10000);                 // 最大容量
recorder.setWindowSize(60);                     // 窗口大小（秒）
```

### 2. 创建客户端实例

```java
// 直接new对象，不依赖Spring
IHotKeyClient hotKeyClient = new HotKeyClient(config);
```

### 3. 基本使用

#### 3.1 判断是否为热Key

```java
String key = "user:123";
boolean isHot = hotKeyClient.isHotKey(key);
```

#### 3.2 从本地缓存获取值

```java
// 只有热Key才会从缓存获取
String value = hotKeyClient.getFromCache(key);
if (value != null) {
    // 缓存命中，直接使用
    return value;
} else {
    // 缓存未命中，需要从Redis获取
    value = redisTemplate.opsForValue().get(key);
    // 处理获取到的值...
}
```

#### 3.3 记录访问日志

```java
// 记录访问，用于统计和热Key检测
hotKeyClient.recordAccess(key);
```

#### 3.4 处理Redis get操作

```java
// 方式1：只传入key，如果热Key且缓存中有值，返回缓存值；否则返回null
String cachedValue = hotKeyClient.handleGet(key);
if (cachedValue != null) {
    return cachedValue;
}

// 从Redis获取
String value = redisTemplate.opsForValue().get(key);
// 方式2：传入key和从Redis获取的值，如果热Key会保存到缓存
return hotKeyClient.handleGet(key, value);
```

#### 3.5 处理Redis set操作

```java
// 先写入Redis
redisTemplate.opsForValue().set(key, value);

// 处理热Key缓存（如果是热Key，会更新本地缓存）
hotKeyClient.handleSet(key, value);
```

#### 3.6 处理Redis delete操作

```java
// 先删除Redis
redisTemplate.delete(key);

// 处理热Key缓存（如果是热Key，会从本地缓存删除）
hotKeyClient.handleDelete(key);
```

## 完整示例

```java
import cn.techwolf.datastar.hotkey.config.HotKeyConfig;
import cn.techwolf.datastar.hotkey.HotKeyClient;
import cn.techwolf.datastar.hotkey.IHotKeyClient;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisService {
    private final StringRedisTemplate redisTemplate;
    private final IHotKeyClient hotKeyClient;

    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        // 初始化热Key客户端
        HotKeyConfig config = new HotKeyConfig();
        config.setEnabled(true);
        // ... 配置参数 ...
        this.hotKeyClient = new HotKeyClient(config);
    }

    public String get(String key) {
        // 1. 先尝试从热Key缓存获取
        String value = hotKeyClient.handleGet(key);
        if (value != null) {
            return value;
        }

        // 2. 从Redis获取
        value = redisTemplate.opsForValue().get(key);

        // 3. 如果获取到值，处理热Key缓存
        return hotKeyClient.handleGet(key, value);
    }

    public void set(String key, String value) {
        // 1. 先写入Redis
        redisTemplate.opsForValue().set(key, value);

        // 2. 处理热Key缓存
        hotKeyClient.handleSet(key, value);
    }

    public void delete(String key) {
        // 1. 先删除Redis
        redisTemplate.delete(key);

        // 2. 处理热Key缓存
        hotKeyClient.handleDelete(key);
    }
}
```

## 高级功能

### 获取内部组件（用于监控和统计）

```java
HotKeyClient client = (HotKeyClient) hotKeyClient;

// 获取访问记录器（用于查看访问统计）
IAccessRecorder recorder = client.getAccessRecorder();
Map<String, Double> stats = recorder.getAccessStatistics();
Set<String> hotKeys = recorder.getHotKeys();

// 获取热Key筛选器（用于查看当前热Key列表）
IHotKeyFilter filter = client.getHotKeyFilter();
Set<String> currentHotKeys = filter.getHotKeys();

// 获取数据存储（用于查看缓存状态）
IHotKeyStorage storage = client.getHotKeyStorage();
long cacheSize = storage.size();
```

### 关闭客户端

```java
// 关闭客户端，停止定时任务，释放资源
if (hotKeyClient instanceof HotKeyClient) {
    ((HotKeyClient) hotKeyClient).shutdown();
}
```

## 配置说明

### 检测配置（Detection）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| windowSize | int | 60 | 统计窗口大小（秒） |
| topN | int | 10 | Top N数量，每5秒晋升Top N个热Key |
| hotKeyQpsThreshold | double | 3000.0 | 热Key QPS阈值（次/秒） |
| warmKeyQpsThreshold | double | 500.0 | 温Key QPS阈值（次/秒） |
| promotionInterval | long | 5000 | 晋升间隔（毫秒），每5秒执行一次 |
| demotionInterval | long | 60000 | 降级间隔（毫秒），每分钟执行一次 |

### 存储配置（Storage）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| enabled | boolean | true | 是否启用数据存储 |
| maximumSize | long | 100 | 最大缓存数量 |
| expireAfterWrite | int | 60 | 写入后过期时间（秒） |

### 记录器配置（Recorder）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| maxCapacity | int | 10000 | 访问记录最大容量 |
| windowSize | int | 60 | 统计窗口大小（秒） |

## 注意事项

1. **线程安全**：`HotKeyClient` 是线程安全的，可以在多线程环境中使用
2. **定时任务**：客户端内部会启动定时任务，用于热Key的晋升和降级
3. **资源释放**：应用关闭时，建议调用 `shutdown()` 方法释放资源
4. **性能考虑**：热Key缓存使用本地内存，建议根据实际需求调整 `maximumSize`
5. **QPS阈值**：根据实际业务场景调整 `hotKeyQpsThreshold`，避免误判或漏判

## 最佳实践

1. **合理设置QPS阈值**：根据业务特点设置合适的热Key QPS阈值
2. **监控热Key数量**：定期检查热Key列表，了解业务热点
3. **调整缓存大小**：根据热Key数量调整 `maximumSize`，避免内存溢出
4. **关注访问统计**：通过 `getAccessRecorder()` 获取访问统计，分析业务模式
