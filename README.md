# Redis热Key检测与本地缓存Spring Boot Starter

## 简介

这是一个用于检测Redis热Key并提供本地缓存的Spring Boot Starter。当某些Redis key访问频率过高时，会自动将其缓存到本地Caffeine缓存中，减少对Redis的访问压力。

## 功能特性

- ✅ **自动热Key检测**：基于滑动窗口算法，自动检测访问频率高的Redis key（QPS >= 1000，默认阈值）
- ✅ **本地缓存**：使用Caffeine实现高性能本地缓存，默认60秒过期
- ✅ **自动刷新**：定时刷新热Key数据，保证数据新鲜度（默认10秒刷新一次）
- ✅ **自动淘汰**：支持时间过期、容量限制等多种淘汰策略，自动移除不再热门的key
- ✅ **智能采样**：支持低频率key的采样机制，降低内存占用
- ✅ **零侵入**：通过HotKeyClient统一处理，无需修改业务代码
- ✅ **可配置**：支持丰富的配置参数，灵活适应不同场景
- ✅ **监控统计**：提供监控接口，实时查看热Key状态和系统指标
- ✅ **命中率统计**：实时统计热Key缓存命中率、QPS、流量占比等指标
- ✅ **JMX监控**：支持通过JConsole、VisualVM等工具查看监控数据
- ✅ **性能优化**：使用CAS无锁算法，避免高并发下的阻塞，提升TP99性能

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
  enabled: true                          # 是否启用热Key检测，默认true
  detection:
    top-n: 20                             # Top N数量，默认20
    hot-key-qps-threshold: 1000.0          # 热Key QPS阈值（次/秒），默认1000.0
    warm-key-qps-threshold: 500.0         # 温Key QPS阈值（次/秒），默认500.0
    promotion-interval: 5000               # 晋升间隔（毫秒），默认5000（5秒）
  storage:
    maximum-size: 200                      # 最大缓存数量，默认200
    expire-after-write: 60                # 写入后过期时间（秒），默认60秒
  recorder:
    max-capacity: 100000                   # 访问记录最大容量，默认100000
    window-size: 10                       # 统计窗口大小（秒），用于计算QPS，默认10秒
    inactive-expire-time: 120              # 非活跃key过期时间（秒），默认120秒
  refresh:
    interval: 10000                        # 刷新间隔（毫秒），默认10000（10秒）
  monitor:
    enabled: true                          # 是否启用监控，默认true
    interval: 60000                        # 监控输出间隔（毫秒），默认60000（60秒）
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
config.getDetection().setHotKeyQpsThreshold(1000.0);
config.getDetection().setTopN(20);
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
| top-n | int | 20 | Top N数量，默认20 |
| hot-key-qps-threshold | double | 1000.0 | 热Key QPS阈值（次/秒），只有QPS >= 1000的key才能成为热Key |
| warm-key-qps-threshold | double | 500.0 | 温Key QPS阈值（次/秒），冷key需要QPS >= 500才能升级到温key |
| promotion-interval | long | 5000 | 晋升间隔（毫秒），默认5000（5秒） |

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
| inactive-expire-time | int | 120 | 非活跃key过期时间（秒），超过此时间未访问的key会被清理 |

### 刷新配置（refresh）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| interval | long | 10000 | 刷新间隔（毫秒），默认10秒刷新一次热Key数据 |

### 监控配置（monitor）

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| enabled | boolean | true | 是否启用监控 |
| interval | long | 60000 | 监控输出间隔（毫秒），默认60秒输出一次 |

## JMX 监控

系统提供了 JMX MBean 来暴露监控数据，支持通过 JConsole、VisualVM 等工具查看实时监控信息。

### MBean 信息

- **MBean 名称**：`cn.techwolf.datastar.hotkey:type=HotKeyMonitor`
- **自动注册**：当监控功能启用时，MBean 会自动注册到 MBeanServer（由Spring管理）

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
| `getTotalWrapGetCount()` | long | wrapGet总调用次数 |
| `getWrapGetQps()` | double | wrapGet的QPS（每秒请求数） |
| `getKeysPerSecond()` | double | 每秒访问的不同key数量 |
| `getHotKeyAccessCount()` | long | 热Key访问总次数 |
| `getHotKeyHitCount()` | long | 热Key缓存命中次数 |
| `getHotKeyMissCount()` | long | 热Key缓存未命中次数 |
| `getHotKeyHitRate()` | double | 热Key命中率（0.0-1.0） |
| `getHotKeyHitRatePercent()` | double | 热Key命中率（百分比） |
| `getHotKeyTrafficRatio()` | double | 热Key流量占比（0.0-1.0） |
| `getHotKeyTrafficRatioPercent()` | double | 热Key流量占比（百分比） |
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
- `totalWrapGetCount`: wrapGet总调用次数
- `wrapGetQps`: wrapGet的QPS（每秒请求数）
- `keysPerSecond`: 每秒访问的不同key数量
- `hotKeyAccessCount`: 热Key访问总次数
- `hotKeyHitCount`: 热Key缓存命中次数
- `hotKeyMissCount`: 热Key缓存未命中次数
- `hotKeyHitRate`: 热Key命中率（0.0-1.0）
- `hotKeyTrafficRatio`: 热Key流量占比（0.0-1.0）

## 使用场景

本系统特别适用于以下高并发、读多写少的业务场景：

### 场景一：秒杀/抢购活动

**业务特点**：
- 活动开始瞬间，大量用户同时访问同一商品库存信息
- 商品库存key（如 `stock:product:12345`）访问QPS可能达到数万甚至数十万
- 读操作占主导，写操作（扣减库存）相对较少
- 对响应时间要求极高，需要毫秒级响应

**解决方案**：
```java
@Service
public class SeckillService {
    @Autowired
    private IHotKeyClient hotKeyClient;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 获取商品库存（自动热Key缓存）
     */
    public Integer getStock(Long productId) {
        String key = "stock:product:" + productId;
        String stockStr = hotKeyClient.wrapGet(key, 
            k -> redisTemplate.opsForValue().get(k));
        return stockStr != null ? Integer.parseInt(stockStr) : 0;
    }
    
    /**
     * 扣减库存
     */
    public boolean decreaseStock(Long productId, Integer quantity) {
        String key = "stock:product:" + productId;
        // 先扣减Redis库存
        Long result = redisTemplate.opsForValue().decrement(key, quantity);
        if (result != null && result >= 0) {
            // 如果是热Key，自动更新本地缓存
            hotKeyClient.updateCache(key, String.valueOf(result));
            return true;
        }
        return false;
    }
}
```

**效果**：
- 商品库存信息自动缓存到本地，减少99%以上的Redis访问
- 响应时间从Redis网络延迟（1-5ms）降低到本地内存访问（<0.1ms）
- 大幅降低Redis压力，避免Redis成为性能瓶颈
- 支持更高的并发量，提升系统整体吞吐量

### 场景二：热门商品/内容查询

**业务特点**：
- 电商平台中，热门商品详情、价格等信息被频繁查询
- 内容平台中，热门文章、视频等元数据访问量巨大
- 同一商品/内容在短时间内被大量用户访问
- 数据更新频率相对较低，适合缓存

**解决方案**：
```java
@Service
public class ProductService {
    @Autowired
    private IHotKeyClient hotKeyClient;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 获取商品详情（自动热Key缓存）
     */
    public ProductInfo getProductInfo(Long productId) {
        String key = "product:info:" + productId;
        String productJson = hotKeyClient.wrapGet(key, 
            k -> redisTemplate.opsForValue().get(k));
        
        if (productJson != null) {
            return JSON.parseObject(productJson, ProductInfo.class);
        }
        return null;
    }
    
    /**
     * 获取商品价格（自动热Key缓存）
     */
    public BigDecimal getProductPrice(Long productId) {
        String key = "product:price:" + productId;
        String priceStr = hotKeyClient.wrapGet(key, 
            k -> redisTemplate.opsForValue().get(k));
        return priceStr != null ? new BigDecimal(priceStr) : null;
    }
}
```

**效果**：
- 热门商品信息自动缓存，命中率可达95%以上
- 减少Redis访问压力，降低网络延迟
- 提升用户体验，响应速度更快
- 自动识别热门商品，无需手动配置

### 场景三：用户信息/配置查询

**业务特点**：
- 用户登录后，频繁查询用户基本信息、权限配置等
- 用户会话信息、个性化配置等被大量访问
- 同一用户信息在短时间内被多次查询
- 数据相对稳定，更新频率低

**解决方案**：
```java
@Service
public class UserService {
    @Autowired
    private IHotKeyClient hotKeyClient;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 获取用户信息（自动热Key缓存）
     */
    public UserInfo getUserInfo(Long userId) {
        String key = "user:info:" + userId;
        String userJson = hotKeyClient.wrapGet(key, 
            k -> redisTemplate.opsForValue().get(k));
        
        if (userJson != null) {
            return JSON.parseObject(userJson, UserInfo.class);
        }
        return null;
    }
    
    /**
     * 获取用户权限配置（自动热Key缓存）
     */
    public UserPermission getUserPermission(Long userId) {
        String key = "user:permission:" + userId;
        String permissionJson = hotKeyClient.wrapGet(key, 
            k -> redisTemplate.opsForValue().get(k));
        
        if (permissionJson != null) {
            return JSON.parseObject(permissionJson, UserPermission.class);
        }
        return null;
    }
}
```

**效果**：
- 活跃用户信息自动缓存，减少Redis查询
- 提升接口响应速度，改善用户体验
- 降低Redis负载，节省服务器资源
- 自动适应访问模式，无需人工干预

### 场景四：排行榜/实时统计

**业务特点**：
- 游戏排行榜、直播打赏榜等实时数据被频繁查询
- 统计数据更新频率较高，但查询频率更高
- 同一排行榜数据在短时间内被大量用户访问
- 对数据实时性有一定要求，但可以接受短暂延迟

**解决方案**：
```java
@Service
public class RankingService {
    @Autowired
    private IHotKeyClient hotKeyClient;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 获取排行榜数据（自动热Key缓存）
     * 自动刷新机制保证数据新鲜度（默认10秒刷新一次）
     */
    public List<RankingItem> getRanking(String rankingType) {
        String key = "ranking:" + rankingType;
        String rankingJson = hotKeyClient.wrapGet(key, 
            k -> redisTemplate.opsForValue().get(k));
        
        if (rankingJson != null) {
            return JSON.parseArray(rankingJson, RankingItem.class);
        }
        return Collections.emptyList();
    }
}
```

**效果**：
- 排行榜数据自动缓存，减少Redis访问
- 自动刷新机制保证数据新鲜度（默认10秒刷新）
- 大幅提升查询性能，支持更高并发
- 降低Redis压力，提升系统稳定性

### 配置建议

针对不同场景，可以调整以下配置参数：

**秒杀/抢购场景**：
```yaml
hotkey:
  detection:
    hot-key-qps-threshold: 5000.0    # 提高阈值，只缓存最热门的商品
    top-n: 30                         # 增加Top N数量，支持更多热门商品
  storage:
    maximum-size: 500                 # 增加缓存容量
    expire-after-write: 30           # 缩短过期时间，保证数据新鲜度
  refresh:
    interval: 5000                   # 缩短刷新间隔，提高数据实时性
```

**热门商品/内容查询场景**：
```yaml
hotkey:
  detection:
    hot-key-qps-threshold: 1000.0    # 使用默认阈值
    top-n: 50                         # 增加Top N数量
  storage:
    maximum-size: 1000                # 增加缓存容量
    expire-after-write: 60           # 使用默认过期时间
  refresh:
    interval: 10000                   # 使用默认刷新间隔
```

**用户信息查询场景**：
```yaml
hotkey:
  detection:
    hot-key-qps-threshold: 500.0     # 降低阈值，缓存更多用户
    top-n: 100                        # 增加Top N数量
  storage:
    maximum-size: 2000                # 增加缓存容量
    expire-after-write: 120           # 延长过期时间，用户信息变化不频繁
  refresh:
    interval: 30000                   # 延长刷新间隔，用户信息更新不频繁
```

## 工作原理

### 核心流程

1. **访问统计**：每次调用 `wrapGet` 方法时，异步记录访问统计（使用滑动窗口算法计算QPS）
2. **热Key检测**：
   - 每5秒执行一次热Key晋升：从满足QPS阈值（>= 1000）的key中，按QPS降序排序，取Top N个（默认20个）晋升为热Key
   - 每60秒执行一次热Key降级：移除QPS低于阈值（< 1000）的key
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
- **模块五：监控器（Monitor）**：监控热key列表、数据存储层大小、访问记录模块数据量，提供命中率统计
- **模块六：缓存数据更新器（Updater）**：存储数据获取回调函数，定时刷新热Key数据

### 智能采样机制

为了降低内存占用，系统实现了智能采样机制：

- **低频率key采样**：QPS < 10的key按10%的比例采样记录
- **快速准入**：QPS >= 50的key直接准入，不进行采样
- **强制准入**：被采样拒绝10000次后强制准入，避免高频率key因采样被拒绝
- **一致性采样**：使用key的hash值进行采样，保证同一个key的采样结果一致
- **动态调整**：容量使用率低于50%时，采样率提高2倍

### 性能优化

系统实现了多项性能优化，提升高并发场景下的性能：

- **CAS无锁算法**：使用CAS操作替代同步块，避免高并发下的阻塞
  - 窗口重置采用无锁算法，只有真正需要重置的线程才执行重置
  - 其他线程在重置期间可以继续执行，不会被阻塞
- **原子操作优化**：使用 `putIfAbsent` 替代 `containsKey + put`，保证原子操作
- **内存优化**：
  - 限制 `windowKeys` 最大大小（100000），防止OOM
  - 定期清理过期key（每5秒），防止内存泄漏
  - 超过限制时拒绝新key，保护系统稳定性
- **异步处理**：所有统计和缓存操作都是异步的，不阻塞主流程

## 监控信息

监控信息会定期输出到日志（默认每60秒）：

```
========== 热Key监控统计 ==========
热Key数量: 5
热Key列表: [user:123, user:456, ...]
数据存储层大小: 5
访问记录模块数据量: 100
访问记录模块内存大小(估算): 10000 bytes
缓存数据更新器注册表数据量: 5
缓存数据更新器注册表内存大小(估算): 1000 bytes
wrapGet总调用次数: 40700
wrapGet的QPS: 678.33
每秒访问的不同key数量: 2.10
热Key访问总次数: 22900
热Key缓存命中次数: 22885
热Key缓存未命中次数: 15
热Key命中率: 99.93%
热Key流量占比: 56.27%
====================================
```

也可以通过代码获取监控信息：

```java
@Autowired
private IHotKeyMonitor hotKeyMonitor;

MonitorInfo info = hotKeyMonitor.getMonitorInfo();
System.out.println("热Key数量: " + info.getHotKeyCount());
System.out.println("命中率: " + info.getHotKeyHitRate());
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
   - 命中率统计模块会限制 `windowKeys` 最大大小（100000），防止OOM

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
   - CAS无锁算法，避免高并发下的阻塞，提升TP99性能

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
- ✅ 实现命中率统计功能（QPS、命中率、流量占比等）
- ✅ 实现JMX监控支持（通过JConsole、VisualVM查看）
- ✅ 性能优化：CAS无锁算法、原子操作优化、内存优化

## 许可证

Copyright © 2024 TechWolf
