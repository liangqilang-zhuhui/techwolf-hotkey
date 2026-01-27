# Redis热Key检测与本地缓存完整设计文档

## 目录

1. [背景与目标](#1-背景与目标)
2. [整体架构设计](#2-整体架构设计)
3. [模块职责说明](#3-模块职责说明)
4. [核心流程设计](#4-核心流程设计)
5. [热Key发现机制](#5-热key发现机制)
6. [本地缓存机制](#6-本地缓存机制)
7. [配置参数说明](#7-配置参数说明)
8. [使用指南](#8-使用指南)
9. [性能特性](#9-性能特性)
10. [监控与调试](#10-监控与调试)
11. [故障排查](#11-故障排查)
12. [注意事项](#12-注意事项)

---

## 1. 背景与目标

### 1.1 背景

在高并发场景下，某些Redis key会收到大量访问请求，形成"热key"问题。热key会导致：
- Redis单实例压力过大，可能造成性能瓶颈
- 网络带宽被大量占用
- 可能导致Redis连接池耗尽
- 影响整体系统性能和稳定性

### 1.2 目标

- **热Key发现**：实时检测并识别访问频率高的Redis key
- **本地缓存**：将热key数据缓存到本地Caffeine缓存，减少对Redis的访问
- **自动淘汰**：实现合理的缓存淘汰机制，保证数据新鲜度
- **性能优化**：降低Redis访问压力，提升系统整体性能
- **优雅解耦**：通过HotKeyClient统一处理，实现与RedisClientManager的解耦
- **面向接口**：采用面向接口编程，实现高内聚、低耦合

---

## 2. 整体架构设计

### 2.1 核心组件架构

```
RedisClientManager (demo/)
    ↓ (使用接口 IHotKeyClient)
HotKeyClient (根包，协调者，实现 IHotKeyClient)
    ├── IHotKeyManager (manager/接口)
    │   └── HotKeyManager (manager/实现)
    │       ├── IAccessRecorder (recorder/接口，依赖)
    │       ├── IHotKeyFilter (filter/接口，依赖)
    │       └── IHotKeyStorage (storage/接口，依赖)
    │
    ├── IAccessRecorder (recorder/接口)
    │   └── AccessRecorder (recorder/实现)
    │       └── HotKeyConfig (config/配置)
    │
    ├── IHotKeyFilter (filter/接口)
    │   └── HotKeyFilter (filter/实现)
    │       ├── IAccessRecorder (recorder/接口，依赖)
    │       └── HotKeyConfig (config/配置)
    │
    ├── IHotKeyStorage (storage/接口)
    │   └── HotKeyStorage (storage/实现)
    │       └── HotKeyConfig.Storage (config/配置)
    │
    └── IHotKeyMonitor (monitor/接口)
        └── HotKeyMonitor (monitor/实现)
            ├── IHotKeyFilter (filter/接口，依赖)
            ├── IHotKeyStorage (storage/接口，依赖)
            ├── IAccessRecorder (recorder/接口，依赖)
            └── HotKeyConfig (config/配置)
    
    └── IGetterRegistry (getter/接口)
        └── GetterRegistry (getter/实现)
```

### 2.2 包结构设计

```
hotkey/
├── IHotKeyClient            # 热Key客户端接口（根包）
├── HotKeyClient             # 热Key客户端实现（协调者，根包）
│
├── config/                  # 配置模块
│   └── HotKeyConfig         # 热Key配置类
│
├── manager/                 # 模块一：热Key管理器
│   ├── IHotKeyManager       # 热Key管理器接口
│   └── HotKeyManager        # 热Key管理器实现
│
├── storage/                 # 模块二：数据存储
│   ├── IHotKeyStorage       # 数据存储接口
│   └── HotKeyStorage        # 数据存储实现
│
├── recorder/                # 模块三：访问记录
│   ├── IAccessRecorder      # 访问记录器接口
│   └── AccessRecorder       # 访问记录器实现
│
├── filter/                  # 模块四：筛选器
│   ├── IHotKeyFilter        # 热Key筛选器接口
│   └── HotKeyFilter         # 热Key筛选器实现
│
├── monitor/                 # 模块五：监控器
│   ├── IHotKeyMonitor       # 热Key监控器接口
│   └── HotKeyMonitor        # 热Key监控器实现
│
├── getter/                  # 模块六：RedisGetter注册表
│   ├── IGetterRegistry      # RedisGetter注册表接口
│   └── GetterRegistry        # RedisGetter注册表实现
│
└── demo/                    # Demo模块
    ├── RedisClientManager    # Redis客户端管理器
    └── ProtoBufCompatibleJsonUtils # JSON工具类
```

### 2.3 依赖关系

**依赖原则**：
- 所有依赖都通过接口，实现面向接口编程
- 单向依赖，避免循环依赖
- 协调者模式：HotKeyClient协调各个模块
- 高内聚：相关功能集中在同一包
- 低耦合：包之间通过接口交互

**依赖图**：
```
RedisClientManager (demo/)
    └── IHotKeyClient (根包接口)
        └── HotKeyClient (根包，协调者)
            ├── IHotKeyManager (manager/接口)
            │   └── HotKeyManager (manager/实现)
            │       ├── IAccessRecorder (recorder/接口)
            │       ├── IHotKeyFilter (filter/接口)
            │       └── IHotKeyStorage (storage/接口)
            │
            ├── IAccessRecorder (recorder/接口)
            │   └── AccessRecorder (recorder/实现)
            │
            ├── IHotKeyFilter (filter/接口)
            │   └── HotKeyFilter (filter/实现)
            │       └── IAccessRecorder (recorder/接口)
            │
            └── IHotKeyStorage (storage/接口)
                └── HotKeyStorage (storage/实现)

HotKeyMonitor (monitor/)
    ├── IHotKeyFilter (filter/接口)
    ├── IHotKeyStorage (storage/接口)
    ├── IAccessRecorder (recorder/接口)
    └── HotKeyConfig (config/)

GetterRegistry (getter/)
    └── 无外部依赖（只依赖Java标准库）

RedisClientManager (demo/)
    └── IGetterRegistry (getter/接口)

HotKeyCacheRefreshService (demo/)
    └── IGetterRegistry (getter/接口)
```

---

## 3. 模块职责说明

### 3.1 HotKeyClient（协调者）

**职责**：热Key客户端，对外提供统一API，协调各个模块

**关键特性**：
- 协调者模式：只协调，不直接操作细节
- 自己初始化：构造函数中自己创建所有依赖组件
- 依赖接口：所有依赖都通过接口，低耦合
- 异步处理：所有统计和缓存操作都是异步的
- 不依赖Spring：可以直接new对象使用

**主要方法**：
- `wrapGet(String key, Supplier<String> redisGetter)`: 包装get操作，自动处理热Key缓存逻辑
- `recordAccess(String key)`: 记录访问日志
- `getFromCache(String key)`: 从本地缓存获取
- `updateCache(String key, String value)`: 更新本地缓存
- `isEnabled()`: 检查是否启用

**初始化逻辑**：
```java
public HotKeyClient(HotKeyConfig config) {
    this.config = config;
    this.enabled = config.isEnabled();

    if (enabled) {
        // 1. 初始化访问记录器（模块三）
        this.accessRecorder = new AccessRecorder(config);
        
        // 2. 初始化数据存储（模块二）
        this.hotKeyStorage = new HotKeyStorage(config);
        
        // 3. 初始化热Key筛选器（模块四）
        this.hotKeyFilter = new HotKeyFilter(accessRecorder, config);
        
        // 4. 初始化热Key管理器（模块一）
        this.hotKeyManager = new HotKeyManager(accessRecorder, hotKeyFilter, hotKeyStorage);
        
        // 5. 启动定时任务
        startScheduledTasks();
    }
}
```

### 3.2 HotKeyManager（模块一：热Key管理器）

**职责**：
- 记录访问日志：调用AccessRecorder记录所有key的访问
- 判断是否热key：从HotKeyFilter获取热key列表
- 处理Redis操作：与Redis Client集成，处理get/set/delete操作

**关键特性**：
- 异步记录访问日志，不阻塞主流程
- 通过HotKeyFilter判断是否为热Key
- 只处理热Key的缓存操作

**主要方法**：
- `recordAccess(String key)`: 记录访问日志（异步）
- `isHotKey(String key)`: 判断是否为热Key
- `handleGet(String key)`: 处理Redis get操作
- `handleSet(String key, String value)`: 处理Redis set操作
- `handleDelete(String key)`: 处理Redis delete操作

### 3.3 HotKeyStorage（模块二：数据存储）

**职责**：
- 只存储热key的value
- 使用Caffeine本地缓存
- 1分钟过期机制

**关键特性**：
- 使用Caffeine实现高性能本地缓存
- 支持过期时间、容量限制
- 提供统计信息收集

**主要方法**：
- `get(String key)`: 从缓存获取
- `put(String key, String value)`: 写入缓存
- `update(String key, String value)`: 更新缓存
- `remove(String key)`: 删除缓存
- `clear()`: 清空缓存
- `size()`: 获取缓存大小

### 3.4 AccessRecorder（模块三：访问记录）

**职责**：
- 记录所有key的访问统计
- 计算QPS（每秒访问量）
- 区分温、热key：
  - 冷key：QPS < 500
  - 温key：QPS >= 500 且 < 3000
  - 热key：QPS >= 3000

**关键特性**：
- 使用滑动窗口算法计算QPS
- 使用LongAdder提高并发性能
- 使用ConcurrentHashMap存储访问统计，线程安全

**主要方法**：
- `recordAccess(String key)`: 记录访问统计
- `getAccessStatistics()`: 获取所有key的QPS统计
- `getWarmKeys()`: 获取温key列表
- `getHotKeys()`: 获取热key列表
- `size()`: 获取统计数量
- `clear()`: 清空统计

**数据结构**：
```java
AccessInfo {
    String key;                    // Redis key
    LongAdder accessCount;         // 访问次数（高性能并发计数）
    volatile long windowStartTime; // 当前窗口开始时间
    volatile long lastAccessTime;  // 最后访问时间
    volatile double qps;           // 访问频率（次/秒）
    long windowSize;               // 窗口大小（秒）
}
```

### 3.5 HotKeyFilter（模块四：筛选器）

**职责**：
1. **晋升热Key**（每5秒执行一次）：
   - 从AccessRecorder获取所有访问统计
   - 筛选QPS >= 3000的key
   - 按QPS降序排序，取Top 10
   - 晋升为热key

2. **降级和淘汰**（每分钟执行一次）：
   - 热key中QPS < 3000的，从热key移除
   - 如果容量超限，按LRU淘汰访问量最低的key

**关键特性**：
- 使用定时任务定期更新热Key列表
- 使用volatile Set保证可见性
- 使用synchronized保证更新操作的原子性

**主要方法**：
- `promoteHotKeys()`: 晋升热Key（定时任务，每5秒）
- `demoteAndEvict()`: 降级和淘汰（定时任务，每分钟）
- `getHotKeys()`: 获取当前热Key列表

### 3.6 HotKeyMonitor（模块五：监控器）

**职责**：
- 每分钟监控并输出：
  - 热key列表和数量
  - 数据存储层大小
  - 访问记录模块数据量和大小

**关键特性**：
- 只读操作（监控），低耦合（通过接口依赖）
- 定期执行监控和清理检查（定时任务）
- 监控缓存状态

**主要方法**：
- `monitor()`: 定期输出监控信息（定时任务，每分钟）
- `getMonitorInfo()`: 获取监控信息（用于接口暴露）

### 3.7 HotKeyConfig（配置模块）

**职责**：热Key功能配置管理

**关键特性**：
- 使用Spring Boot的`@ConfigurationProperties`自动绑定配置
- 提供配置参数的默认值
- 使用嵌套内部类组织配置（Detection、Storage、Recorder、Monitor）

**主要配置项**：
- `enabled`: 是否启用热Key检测
- `detection`: 检测相关配置（窗口大小、TopN、阈值等）
- `storage`: 本地缓存配置（容量、过期时间等）
- `recorder`: 访问记录配置（容量、窗口大小等）
- `monitor`: 监控配置（监控间隔等）

### 3.8 GetterRegistry（模块六：RedisGetter注册表）

**职责**：
- 存储和管理每个Redis key对应的`Supplier<String> redisGetter`回调函数
- 供刷新服务等场景复用redisGetter，避免重复创建

**关键特性**：
- **高内聚**：所有redisGetter相关的存储和管理功能都集中在此模块
- **低耦合**：只依赖Java标准库，不依赖具体业务实现
- **责任清晰**：只负责存储和管理，不涉及业务逻辑
- **线程安全**：使用ConcurrentHashMap，支持高并发读写
- **高性能**：所有操作都是O(1)时间复杂度

**主要方法**：
- `register(String key, Supplier<String> redisGetter)`: 注册redisGetter
- `get(String key)`: 获取指定key的redisGetter
- `remove(String key)`: 移除指定key的redisGetter
- `getAllKeys()`: 获取所有已注册的key
- `clear()`: 清空所有注册的redisGetter
- `size()`: 获取注册表大小
- `contains(String key)`: 检查key是否已注册

**使用场景**：
1. **注册阶段**：在`RedisClientManager.get()`方法中，调用`wrapGet`时注册redisGetter
2. **复用阶段**：在刷新服务中，从注册表获取redisGetter进行主动刷新
3. **清理阶段**：在删除key时，从注册表移除redisGetter释放资源

**设计优势**：
- 解耦：刷新服务不依赖RedisClientManager的具体实现
- 复用：避免重复创建redisGetter，提高效率
- 一致性：使用与业务代码相同的redisGetter，保证行为一致

---

## 4. 核心流程设计

### 4.1 读操作流程

**完整流程**：
```
1. 客户端调用 RedisClientManager.get(key)
   ↓
2. 判断热Key功能是否启用
   ├─ 未启用 → 直接走Redis，返回结果
   └─ 已启用 → 继续步骤3
   ↓
3. 调用 hotKeyClient.wrapGet(key, redisGetter)
   ↓
4. 记录访问日志 (hotKeyManager.recordAccess，异步)
   ↓
5. 判断是否为热Key (hotKeyManager.isHotKey)
   ├─ 是热Key → 检查本地缓存 (hotKeyStorage.get)
   │   ├─ 缓存命中 → 直接返回（异步记录统计）
   │   └─ 缓存未命中 → 继续步骤6
   └─ 非热Key → 直接继续步骤6
   ↓
6. 从Redis获取数据 (redisGetter.get，同步)
   ↓
7. 如果是热Key → 写入缓存 (hotKeyStorage.put)
   ↓
8. 返回数据给客户端（不等待异步处理）
```

**关键点**：
- **只有热Key才走本地缓存**：先判断是否为热Key，只有热Key才从本地缓存获取
- **非热Key直接走Redis**：非热Key的所有逻辑和之前一样，只是异步增加了访问统计（用于监控）
- **异步处理不阻塞**：所有统计和缓存操作都是异步的，不影响主流程性能

**代码实现**：
```java
public String wrapGet(String key, Supplier<String> redisGetter) {
    // 1. 记录访问日志（用于热Key检测）
    recordAccess(key);
    
    // 2. 如果是热Key，先从本地缓存获取值
    boolean isHotKey = isHotKey(key);
    String cachedValue = getFromCache(key);
    if (cachedValue != null) {
        // 缓存命中，直接返回
        return cachedValue;
    }
    
    // 3. 缓存未命中，通过回调从Redis获取值
    String value = redisGetter != null ? redisGetter.get() : null;
    
    // 4. 如果从Redis获取到值，且是热Key，更新本地缓存
    if (value != null) {
        updateCache(key, value);
    }
    
    return value;
}
```

### 4.2 写操作流程

**完整流程**：
```
1. 客户端调用 RedisClientManager.set(key, value)
   ↓
2. 先写入Redis (valueOps.set，同步，保证数据一致性)
   ↓
3. 判断热Key功能是否启用
   ├─ 未启用 → 直接返回
   └─ 已启用 → 继续步骤4
   ↓
4. 如果是热Key → 更新本地缓存 (hotKeyStorage.update，异步)
   ↓
5. 记录访问统计 (hotKeyManager.recordAccess，异步)
   ↓
6. 返回操作结果（不等待异步处理）
```

**关键点**：
- Redis写入是同步的，保证数据一致性
- **只有热Key才更新本地缓存**：非热Key不更新缓存
- 所有热Key相关操作都是异步的，不阻塞主流程

### 4.3 删除操作流程

**完整流程**：
```
1. 客户端调用 RedisClientManager.del(key)
   ↓
2. 删除Redis中的key (redisTemplate.delete，同步)
   ↓
3. 判断热Key功能是否启用
   ├─ 未启用 → 直接返回
   └─ 已启用 → 继续步骤4
   ↓
4. 如果是热Key → 从缓存中删除 (hotKeyStorage.remove，异步)
   ↓
5. 返回删除结果
```

**关键点**：
- 先删除Redis，再清理本地缓存
- 异步清理缓存，不阻塞主流程

### 4.4 热Key晋升流程

**完整流程**：
```
1. 定时任务启动（每5秒执行一次，可配置）
   ↓
2. 同步更新（synchronized(updateLock)）：
   - 从AccessRecorder获取所有访问统计
   ↓
3. 过滤和排序：
   - 过滤：QPS >= 热Key阈值（默认3000）
   - 排序：按QPS降序
   - 取TopN：limit(topN，默认10)
   ↓
4. 更新热Key列表（volatile Set<String> hotKeys）
   ↓
5. 记录日志（DEBUG级别）
```

**代码实现**：
```java
@Scheduled(fixedDelayString = "${hotkey.detection.promotion-interval:5000}")
public Set<String> promoteHotKeys() {
    synchronized (updateLock) {
        // 1. 获取访问记录器中的所有访问信息
        Map<String, Double> accessStats = accessRecorder.getAccessStatistics();
        
        // 2. 筛选QPS >= 阈值的key，按QPS降序排序，取Top N
        List<String> candidates = accessStats.entrySet().stream()
                .filter(entry -> entry.getValue() >= hotKeyQpsThreshold)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        // 3. 更新热Key列表
        Set<String> newHotKeys = candidates.stream()
                .filter(key -> !hotKeys.contains(key))
                .collect(Collectors.toSet());
        
        if (newHotKeys.size() > 0) {
            Set<String> updatedHotKeys = new HashSet<>(hotKeys);
            updatedHotKeys.addAll(newHotKeys);
            this.hotKeys = Collections.unmodifiableSet(updatedHotKeys);
        }
        
        return newHotKeys;
    }
}
```

### 4.5 热Key降级和淘汰流程

**完整流程**：
```
1. 定时任务启动（每分钟执行一次，可配置）
   ↓
2. 同步更新（synchronized(updateLock)）：
   - 从AccessRecorder获取所有访问统计
   ↓
3. 降级处理：
   - 热key中QPS < 3000的，从热key移除
   ↓
4. 容量淘汰（如果容量超限）：
   - 按QPS排序，淘汰QPS最低的key（但保留热key）
   ↓
5. 更新热Key列表
   ↓
6. 记录日志（DEBUG级别）
```

---

## 5. 热Key发现机制

### 5.1 访问统计（AccessRecorder）

**设计思路**：
- 使用滑动窗口算法统计每个key的访问频率
- 采用时间分片的方式，将时间划分为多个窗口
- 每个窗口记录该时间段内的访问次数

**高性能设计**：
- **LongAdder替代AtomicLong**：使用`LongAdder`提高高并发场景下的性能
- **无锁设计**：使用`ConcurrentHashMap`的`computeIfAbsent`方法，避免锁竞争
- **滑动窗口**：自动重置过期窗口，保证统计的准确性

**统计维度**：
- **时间窗口**：默认60秒为一个统计周期（可配置）
- **访问计数**：使用`LongAdder`记录每个key在时间窗口内的访问次数
- **访问频率**：访问次数 / 时间窗口长度（次/秒，QPS）

**窗口重置机制**：
- 当访问时间超过窗口大小时，自动重置窗口
- 重置时清空访问计数，重新开始统计
- 保证统计数据的时效性

### 5.2 TopN计算（HotKeyFilter）

**设计思路**：
- 定期（默认每5秒）计算当前访问频率最高的Top N个key
- 使用Stream API进行排序和过滤
- 只保留Top N的热key

**计算策略**：
1. **固定Top N**：始终维护访问频率最高的N个key（默认N=10）
2. **阈值判断**：QPS必须 >= 热Key阈值（默认3000）
3. **排序规则**：按QPS降序排列

**更新频率**：
- **定期更新**：每5秒计算一次Top N（可配置）
- **同步更新**：使用`synchronized`保证更新操作的原子性

### 5.3 阈值判断

**热Key判定标准**：
1. **QPS阈值**：单位时间内访问次数超过阈值（默认：3000次/秒）
2. **Top N排名**：访问频率排名在前N位（默认N=10）
3. **同时满足**：必须同时满足QPS阈值和Top N排名

**温Key判定标准**：
- QPS >= 500 且 < 3000

**冷Key判定标准**：
- QPS < 500

**判断逻辑**：
```java
public boolean isHotKey(String key) {
    // 检查热Key列表（O(1)时间复杂度）
    return hotKeyFilter.getHotKeys().contains(key);
}
```

---

## 6. 本地缓存机制

### 6.1 Caffeine缓存配置

**缓存特性**：
- **最大容量**：限制缓存中最多存储的热key数量（默认100个）
- **过期时间**：热key数据在本地缓存中的存活时间（默认60秒）
- **统计功能**：记录缓存命中率、加载时间等指标

**高性能设计**：
- **无锁设计**：Caffeine内部使用无锁数据结构，保证高性能
- **智能淘汰**：自动使用LRU策略淘汰最少使用的key

### 6.2 缓存加载策略

**加载时机**：
1. **读操作触发**：当从Redis获取数据后，如果判断为热Key，自动写入本地缓存
2. **写操作触发**：当写入Redis后，如果是热Key，异步更新本地缓存

**加载逻辑**：
- **同步加载**：首次加载时，同步从Redis获取数据
- **只缓存热Key**：只有热Key才会被缓存，非热Key的数据不会被写入本地缓存，节省内存

### 6.3 缓存更新策略

**更新场景**：
1. **热Key写入**：当热key被更新时，异步更新本地缓存
2. **自动过期**：Caffeine自动处理过期，无需手动清理

**更新方式**：
- **异步更新**：写操作后异步更新，不阻塞主流程

---

## 7. 配置参数说明

### 7.1 热Key检测配置

```yaml
hotkey:
  enabled: true                          # 是否启用热Key检测
  detection:
    window-size: 60                      # 统计窗口大小（秒），默认60
    top-n: 10                            # Top N数量，默认10
    hot-key-qps-threshold: 3000.0        # 热Key QPS阈值（次/秒），默认3000.0
    warm-key-qps-threshold: 500.0        # 温Key QPS阈值（次/秒），默认500.0
    promotion-interval: 5000             # 晋升间隔（毫秒），默认5000（5秒）
    demotion-interval: 60000              # 降级间隔（毫秒），默认60000（60秒）
    max-stats-capacity: 5000             # 统计信息最大容量，默认5000
    admission-min-frequency: 10.0        # 准入最小访问频率阈值（次/秒），默认10.0
    sampling-rate: 0.1                   # 采样率（0.0-1.0），默认0.1
    fast-admission-threshold: 50.0       # 快速准入阈值（次/秒），默认50.0
    rejected-access-threshold: 10000     # 被拒绝访问强制准入阈值，默认10000
    enable-consistent-sampling: true     # 是否启用一致性采样，默认true
    capacity-usage-threshold: 0.5        # 容量使用率阈值（0.0-1.0），默认0.5
```

**参数说明**：
- `window-size`: 统计窗口大小，用于计算访问频率
- `top-n`: 维护的热Key数量上限
- `hot-key-qps-threshold`: 热Key QPS阈值，超过此值判定为热Key
- `warm-key-qps-threshold`: 温Key QPS阈值
- `promotion-interval`: 热Key晋升间隔，建议5-10秒
- `demotion-interval`: 热Key降级间隔，建议60秒
- `max-stats-capacity`: 统计信息最大容量，建议设置为topN的100-500倍

### 7.2 本地缓存配置

```yaml
hotkey:
  storage:
    enabled: true                        # 是否启用本地缓存，默认true
    maximum-size: 100                    # 最大缓存数量，默认100
    expire-after-write: 60               # 写入后过期时间（秒），默认60
    record-stats: true                   # 是否记录统计信息，默认true
```

**参数说明**：
- `enabled`: 是否启用本地缓存
- `maximum-size`: 缓存最大容量，建议根据内存情况设置
- `expire-after-write`: 缓存过期时间，建议60秒
- `record-stats`: 是否记录统计信息，用于监控

### 7.3 访问记录配置

```yaml
hotkey:
  recorder:
    max-capacity: 10000                  # 访问记录最大容量，默认10000
    window-size: 60                      # 统计窗口大小（秒），默认60
```

**参数说明**：
- `max-capacity`: 访问记录最大容量，限制访问记录的数量，避免内存溢出
- `window-size`: 统计窗口大小，用于计算QPS

### 7.4 监控配置

```yaml
hotkey:
  monitor:
    interval: 60000                      # 监控输出间隔（毫秒），默认60000（60秒）
```

**参数说明**：
- `interval`: 监控信息输出间隔，建议60秒

---

## 8. 使用指南

### 8.1 基本使用

热Key功能已自动集成到`RedisClientManager`，使用前需要先配置和初始化：

**1. 配置热Key功能**（在application.yml中）：

```yaml
hotkey:
  enabled: true
  detection:
    window-size: 60
    top-n: 10
    hot-key-qps-threshold: 3000.0
    warm-key-qps-threshold: 500.0
    promotion-interval: 5000
    demotion-interval: 60000
  storage:
    enabled: true
    maximum-size: 100
    expire-after-write: 60
  recorder:
    max-capacity: 10000
    window-size: 60
  monitor:
    interval: 60000
```

**2. 初始化HotKeyClient并注入到RedisClientManager**：

```java
@Configuration
public class HotKeyConfiguration {
    
    @Autowired
    private HotKeyConfig hotKeyConfig;
    
    @Bean
    public HotKeyClient hotKeyClient() {
        return new HotKeyClient(hotKeyConfig);
    }
    
    @Bean
    public RedisClientManager redisClientManager(StringRedisTemplate redisTemplate) {
        RedisClientManager manager = new RedisClientManager(redisTemplate);
        manager.setHotKeyClient(hotKeyClient());
        return manager;
    }
}
```

**3. 使用RedisClientManager**（无需修改业务代码）：

```java
@Autowired
private RedisClientManager redisClientManager;

// 读操作：自动使用本地缓存和热Key检测
String value = redisClientManager.get("user:123");

// 写操作：自动更新本地缓存和统计
redisClientManager.set("user:123", "value");

// 对象操作：同样支持热Key功能
User user = redisClientManager.get("user:123", User.class);
redisClientManager.set("user:123", user);

// 删除操作：自动清理本地缓存
boolean deleted = redisClientManager.del("user:123");
```

### 8.2 直接使用HotKeyClient

```java
@Autowired
private IHotKeyClient hotKeyClient;

// 包装get操作，自动处理热Key缓存逻辑
String value = hotKeyClient.wrapGet("user:123", () -> redisTemplate.opsForValue().get("user:123"));

// 记录访问日志
hotKeyClient.recordAccess("user:123");

// 从本地缓存获取（仅热Key）
String cachedValue = hotKeyClient.getFromCache("user:123");

// 更新本地缓存（仅热Key）
hotKeyClient.updateCache("user:123", "value");

// 检查是否启用
boolean enabled = hotKeyClient.isEnabled();
```

### 8.3 监控信息获取

**方式1：通过HotKeyMonitor Bean获取**

```java
@Autowired
private HotKeyMonitor hotKeyMonitor;

// 获取监控信息
HotKeyMonitor.MonitorInfo info = hotKeyMonitor.getMonitorInfo();
System.out.println("热Key数量: " + info.getHotKeyCount());
System.out.println("热Key列表: " + info.getHotKeys());
System.out.println("缓存大小: " + info.getStorageSize());
System.out.println("访问记录数量: " + info.getRecorderSize());
```

**方式2：通过日志查看**（自动定期输出，默认每60秒）

监控信息会自动输出到日志，包含所有关键指标。

---

## 9. 性能特性

### 9.1 高性能设计

**无锁设计**：
- 使用`ConcurrentHashMap`的`computeIfAbsent`，避免锁竞争
- 使用`LongAdder`替代`AtomicLong`，提高高并发性能
- 使用`volatile`保证可见性，避免锁

**异步处理**：
- 所有访问统计使用`CompletableFuture.runAsync`异步执行
- 缓存更新和统计记录都是异步的，不阻塞主流程
- 使用独立的线程池执行定时任务

**快速路径优化**：
- 本地缓存命中时，直接返回，异步记录统计
- 热Key判断时，先检查热Key列表（O(1)），再检查访问频率
- 只有热Key才查询本地缓存，减少不必要的查询

### 9.2 内存优化

**统计信息管理**：
- 使用滑动窗口，自动重置过期窗口
- 限制统计信息的数量（maxStatsCapacity）
- 定期清理不活跃的统计信息

**缓存管理**：
- 只缓存热Key，非热Key不占用内存
- 限制缓存容量（maximumSize）
- Caffeine自动使用LRU策略淘汰

### 9.3 性能指标

**预期性能提升**：
- **Redis访问减少**：热Key的Redis访问量减少80%以上
- **响应时间降低**：本地缓存命中时，响应时间降低90%以上
- **吞吐量提升**：系统整体吞吐量提升30%以上

**实际性能**：
- 本地缓存命中：< 1ms（内存访问）
- Redis访问：1-10ms（网络+Redis处理）
- 异步处理开销：< 0.1ms（不阻塞主流程）

---

## 10. 监控与调试

### 10.1 监控信息

**HotKeyMonitor定期输出**（每60秒）：
- 热Key数量
- 热Key列表
- 本地缓存大小
- 访问记录数量
- 访问记录内存大小（估算）

**监控日志示例**：
```
========== 热Key监控统计 ==========
热Key数量: 5
热Key列表: [user:123, user:456, user:789, ...]
数据存储层大小: 5
访问记录模块数据量: 100
访问记录模块内存大小(估算): 10000 bytes
====================================
```

### 10.2 日志级别

- **INFO**：初始化信息、监控统计
- **DEBUG**：详细的处理过程、异常信息
- **WARN**：命中率低、热Key数量异常等警告

### 10.3 监控接口

```java
@Autowired
private HotKeyMonitor hotKeyMonitor;

// 获取监控信息对象
HotKeyMonitor.MonitorInfo info = hotKeyMonitor.getMonitorInfo();

// 监控信息包含：
// - hotKeyCount: 热Key数量
// - hotKeys: 热Key列表
// - storageSize: 缓存大小
// - recorderSize: 访问记录数量
// - recorderMemorySize: 访问记录内存大小（估算）
```

---

## 11. 故障排查

### 11.1 热Key未检测到

**可能原因**：
1. 访问频率未达到阈值
2. TopN更新间隔过长
3. 统计窗口设置不合理

**排查方法**：
1. 检查配置参数（hot-key-qps-threshold、top-n）
2. 查看监控日志，确认访问统计数量
3. 调整TopN更新间隔（promotion-interval）
4. 检查`AccessRecorder.getAccessStatistics()`获取详细统计

### 11.2 缓存命中率低

**可能原因**：
1. 热Key检测不准确
2. 缓存过期时间过短
3. 热Key变化频繁

**排查方法**：
1. 查看监控日志，确认热Key列表
2. 检查缓存过期时间配置（expire-after-write）
3. 分析访问模式，调整检测参数
4. 检查`HotKeyStorage.size()`获取缓存统计

### 11.3 内存占用过高

**可能原因**：
1. 缓存容量设置过大
2. 统计信息未及时清理
3. 热Key数量过多

**排查方法**：
1. 检查缓存容量配置（maximum-size）
2. 查看统计信息数量（通过监控接口）
3. 调整TopN数量，减少热Key数量
4. 检查JVM内存使用情况

### 11.4 性能问题

**可能原因**：
1. 异步处理线程池不足
2. 统计信息过多
3. 缓存操作频繁

**排查方法**：
1. 检查线程池配置
2. 查看统计信息数量
3. 分析缓存操作频率
4. 检查系统资源使用情况

---

## 12. 注意事项

### 12.1 数据一致性

- 本地缓存的数据可能与Redis中的数据存在短暂不一致（最多60秒）
- 对于强一致性要求的场景，需要谨慎使用
- 建议设置较短的过期时间（60秒），平衡一致性和性能
- 写操作会异步更新本地缓存，保证最终一致性

### 12.2 内存占用

- 本地缓存会占用JVM内存，需要合理设置缓存容量
- 统计信息也会占用内存，但会定期清理
- 建议监控内存使用情况，避免OOM
- 默认配置下，内存占用较小（100个key * 平均value大小）

### 12.3 适用场景

**适合场景**：
- **读多写少**：适合读多写少的场景
- **热点数据**：适合有明显热点数据的场景
- **高并发**：适合高并发访问的场景
- **数据变化不频繁**：适合数据变化不频繁的场景

**不适用场景**：
- **强一致性要求**：不适合对数据一致性要求极高的场景
- **写多读少**：不适合写操作远多于读操作的场景
- **无热点数据**：不适合所有key访问频率都差不多的场景

### 12.4 异常处理

**降级策略**：
- 如果热Key功能未启用，直接降级到Redis操作
- 如果热Key处理异常，降级到直接返回Redis结果
- 异常被捕获并记录DEBUG日志，不抛出异常

**异常隔离**：
- 单个key的处理失败不影响其他key
- 异步处理中的异常被捕获，不影响主流程

### 12.5 最佳实践

1. **合理配置参数**：
   - 根据实际业务场景调整阈值和窗口大小
   - 监控缓存命中率，优化配置

2. **监控和告警**：
   - 定期查看监控信息
   - 设置告警规则（命中率低、热Key数量异常等）

3. **性能测试**：
   - 在生产环境前进行性能测试
   - 验证缓存命中率和性能提升

4. **逐步启用**：
   - 建议先在测试环境验证
   - 逐步在生产环境启用
   - 监控系统指标，及时调整

---

## 13. 设计原则总结

### 13.1 面向接口编程

- 所有核心组件都定义接口
- 实现类实现对应接口
- 依赖都通过接口，不依赖具体实现
- 便于测试、扩展和替换

### 13.2 高内聚低耦合

- **高内聚**：相关功能集中在同一包/类
- **低耦合**：通过接口依赖，包之间解耦
- **职责清晰**：每个类/包职责单一

### 13.3 协调者模式

- `HotKeyClient`作为协调者，协调各个模块
- 不直接操作细节，只负责协调
- 统一对外API，简化使用

### 13.4 异步处理

- 所有统计和缓存操作都是异步的
- 不阻塞主流程，保证性能
- 使用`CompletableFuture`实现异步

### 13.5 自己初始化

- `HotKeyClient`自己初始化所有依赖组件
- 不需要外部传入，简化使用
- 配置驱动，灵活可控

---

## 14. 版本历史

### v1.0 (当前版本)

- ✅ 实现热Key检测机制（滑动窗口、TopN计算）
- ✅ 实现本地Caffeine缓存
- ✅ 实现异步处理和统计
- ✅ 实现解耦设计（HotKeyClient）
- ✅ 实现监控和日志
- ✅ 高性能优化（LongAdder、无锁设计）
- ✅ 面向接口编程（接口抽象）
- ✅ 合理分包（按功能模块分包）

---

## 15. 参考资料

- [Caffeine缓存文档](https://github.com/ben-manes/caffeine)
- [LongAdder性能分析](https://www.baeldung.com/java-longadder-and-longaccumulator)
- [Redis热Key问题](https://mp.weixin.qq.com/s/xOzEj5HtCeh_ezHDPHw6Jw)
- [面向接口编程](https://en.wikipedia.org/wiki/Interface-based_programming)
- [协调者模式](https://en.wikipedia.org/wiki/Mediator_pattern)
