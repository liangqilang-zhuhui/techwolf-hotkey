# Redis热Key检测与本地缓存系统设计文档

## 1. 概述

### 1.1 背景
在高并发场景下，某些Redis key会收到大量访问请求，形成"热key"问题。热key会导致：
- Redis单实例压力过大，可能造成性能瓶颈
- 网络带宽被大量占用
- 可能导致Redis连接池耗尽
- 影响整体系统性能和稳定性

### 1.2 目标
- **热Key发现**：实时检测并识别访问频率高的Redis key
- **本地缓存**：将热key数据缓存到本地Caffeine缓存，减少对Redis的访问
- **自动刷新**：定时刷新热Key数据，保证数据新鲜度
- **自动淘汰**：实现合理的缓存淘汰机制，保证数据新鲜度
- **性能优化**：降低Redis访问压力，提升系统整体性能
- **优雅解耦**：通过HotKeyClient统一处理，实现与业务代码的解耦

## 2. 整体架构

### 2.1 模块划分

系统采用分层架构，分为6个核心模块：

**模块一：热Key管理器（manager）**
- 职责：记录访问日志、维护和管理热Key列表、判断是否热key、协调各模块
- 接口：`IHotKeyManager`
- 实现：`HotKeyManager`

**模块二：数据存储（storage）**
- 职责：存储热key的value
- 接口：`IHotKeyStorage`
- 实现：`HotKeyStorage`

**模块三：访问记录（recorder）**
- 职责：记录所有key的访问统计、计算QPS
- 接口：`IAccessRecorder`
- 实现：`AccessRecorder`

**模块四：选择器（selector）**
- 职责：根据访问统计选择哪些key应该晋升为热Key或从热Key中移除
- 接口：`IHotKeySelector`
- 实现：`HotKeySelector`
- 设计：无状态，只负责计算和选择，不维护热Key列表

**模块五：监控器（monitor）**
- 职责：监控热key列表、数据存储层大小、访问记录模块数据量
- 接口：`IHotKeyMonitor`
- 实现：`HotKeyMonitor`

**模块六：缓存数据更新器（updater）**
- 职责：存储和管理每个Redis key对应的数据获取回调函数，并负责自动刷新缓存数据
- 接口：`ICacheDataUpdater`
- 实现：`CacheDataUpdater`

### 2.2 依赖关系

```
HotKeyClient (协调者)
    ├── IHotKeyManager
    │   └── HotKeyManager
    │       ├── IAccessRecorder
    │       ├── IHotKeyStorage
    │       └── ICacheDataUpdater (用于清理被降级key)
    │       └── (维护 hotKeys 列表)
    ├── IAccessRecorder
    │   └── AccessRecorder
    ├── IHotKeySelector
    │   └── HotKeySelector
    │       └── IAccessRecorder
    ├── IScheduler
    │   └── SchedulerManager
    │       ├── IHotKeyManager
    │       └── IHotKeySelector
    ├── IHotKeyStorage
    │   └── HotKeyStorage
    ├── ICacheDataUpdater
    │   └── CacheDataUpdater
    │       └── IHotKeyStorage
    └── HotKeyConfig (配置)
```

**设计原则**：
- 所有依赖都通过接口，实现面向接口编程
- 单向依赖，避免循环依赖
- 协调者模式：HotKeyClient协调各个模块
- 高内聚：相关功能集中在同一模块
- 低耦合：模块之间通过接口交互

## 3. 核心流程

### 3.1 读操作流程

**完整流程**：

1. 客户端调用 `HotKeyClient.wrapGet(key, redisGetter)`
2. 记录访问日志（异步，用于热Key检测）
3. 判断是否为热Key
4. 如果是热Key：
   - 注册redisGetter到注册表（用于自动刷新）
   - 先从本地缓存获取值
   - 缓存命中则直接返回
   - 缓存未命中则继续
5. 从Redis获取数据（调用redisGetter）
6. 如果从Redis获取到值，且是热Key，更新本地缓存
7. 返回数据给客户端

**关键点**：
- 只有热Key才走本地缓存
- 非热Key直接走Redis，只异步记录访问统计
- 热Key的redisGetter会自动注册到注册表
- 所有统计和缓存操作都是异步的，不阻塞主流程

### 3.2 写操作流程

**完整流程**：

1. 客户端调用 `RedisClientManager.set(key, value)`
2. 先写入Redis（同步，保证数据一致性）
3. 判断热Key功能是否启用
4. 如果是热Key，异步更新本地缓存
5. 异步记录访问统计
6. 返回操作结果

**关键点**：
- Redis写入是同步的，保证数据一致性
- 只有热Key才更新本地缓存
- 所有热Key相关操作都是异步的，不阻塞主流程

### 3.3 删除操作流程

**完整流程**：

1. 客户端调用 `RedisClientManager.del(key)`
2. 删除Redis中的key（同步）
3. 判断热Key功能是否启用
4. 如果是热Key，异步从缓存中删除
5. 返回删除结果

**关键点**：
- 先删除Redis，再清理本地缓存
- 异步清理缓存，不阻塞主流程

### 3.4 热Key晋升流程

**完整流程**：

1. 定时任务启动（每5秒执行一次，可配置）
2. SchedulerManager从HotKeyManager获取当前热Key列表
3. SchedulerManager调用HotKeySelector.promoteHotKeys(currentHotKeys)
4. HotKeySelector计算应该晋升的key：
   - 从AccessRecorder获取所有访问统计
   - 过滤：QPS >= 热Key阈值（默认3000）
   - 排序：按QPS降序
   - 取TopN：limit(topN，默认10)
   - 排除已经是热Key的，返回新晋升的key集合
5. SchedulerManager调用HotKeyManager.promoteHotKeys(newHotKeys)
6. HotKeyManager更新热Key列表（使用synchronized保证原子性，volatile Set保证可见性）
7. 记录日志

**关键点**：
- 必须同时满足QPS阈值和Top N排名
- Selector只负责计算，Manager负责状态管理
- 使用synchronized保证更新操作的原子性
- 使用volatile保证可见性

### 3.5 热Key降级流程

**完整流程**：

1. 定时任务启动（每分钟执行一次，可配置）
2. SchedulerManager从HotKeyManager获取当前热Key列表
3. SchedulerManager调用HotKeySelector.demoteAndEvict(currentHotKeys)
4. HotKeySelector计算应该降级的key：
   - 从AccessRecorder获取所有访问统计
   - 检查热key中访问小于阈值的，返回应该移除的key集合
   - 如果容量超限，计算需要淘汰的key（但保留热key）
5. SchedulerManager调用HotKeyManager.demoteHotKeys(removedKeys)
6. HotKeyManager更新热Key列表（使用synchronized保证原子性，volatile Set保证可见性）
7. HotKeyManager自动调用CacheDataUpdater清理被降级key的注册表和失败计数
8. 记录日志

**关键点**：
- Selector只负责计算，Manager负责状态管理
- Manager负责热Key的完整生命周期管理，包括降级时的资源清理
- 降级时自动清理注册表，停止自动刷新
- 清理逻辑内聚在Manager中，简化调用链

### 3.6 自动刷新流程

**完整流程**：

1. 定时任务启动（每10秒执行一次，可配置）
2. 从缓存数据更新器获取所有已注册的key
3. 遍历所有已注册的key
4. 对于每个key：
   - 从缓存数据更新器获取数据获取回调函数
   - 调用数据获取回调函数.apply(key)获取最新值
   - 如果成功，更新本地缓存，清除失败计数
   - 如果失败，记录失败次数
5. 检查失败次数超过阈值的key，自动移除（从缓存、注册表、失败计数中移除）
6. 记录日志

**关键点**：
- 只刷新已注册的key（即热Key）
- 异常隔离：每个key的刷新使用try-catch隔离，单个key失败不影响其他key
- 自动清理：刷新失败超过阈值（默认3次）后，自动移除该热key
- 成功重置：刷新成功时自动清除失败计数

## 4. 模块职责详解

### 4.1 HotKeyClient（协调者）

**职责**：
- 对外提供统一API，协调各个模块
- 初始化所有依赖组件
- 管理定时任务（晋升、降级）
- 启动和停止刷新服务

**关键特性**：
- 协调者模式：只协调，不直接操作细节
- 自己初始化：构造函数中自己创建所有依赖组件
- 依赖接口：所有依赖都通过接口，低耦合
- 异步处理：所有统计和缓存操作都是异步的
- 不依赖Spring：可以直接new对象使用

### 4.2 HotKeyManager（模块一）

**职责**：
- 记录访问日志：调用AccessRecorder记录所有key的访问
- 维护和管理热Key列表：存储当前热Key列表，提供晋升和降级方法
- 判断是否热key：基于自己维护的热Key列表进行判断
- 处理Redis操作：与Redis Client集成，处理get/set/delete操作
- 管理热Key完整生命周期：降级时自动清理相关资源（注册表、失败计数等）

**关键特性**：
- 异步记录访问日志，不阻塞主流程
- 维护热Key列表状态，使用volatile Set保证可见性
- 使用synchronized保证更新操作的原子性
- 只处理热Key的缓存操作
- 降级时自动清理资源，保证热Key生命周期的完整性

### 4.3 HotKeyStorage（模块二：数据存储）

**职责**：
- 只存储热key的value
- 使用Caffeine本地缓存
- 1分钟过期机制

**关键特性**：
- 使用Caffeine实现高性能本地缓存
- 支持过期时间、容量限制
- 提供统计信息收集

### 4.4 AccessRecorder（模块三）

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

### 4.6 HotKeySelector（模块四）

**职责**：
1. **计算应该晋升的热Key**（每5秒执行一次）：
   - 从AccessRecorder获取所有访问统计
   - 筛选QPS >= 3000的key
   - 按QPS降序排序，取Top 10
   - 排除已经是热Key的，返回新晋升的key集合

2. **计算应该降级的热Key**（每分钟执行一次）：
   - 根据当前热Key列表和访问统计
   - 计算热key中QPS < 3000的，返回应该移除的key集合
   - 如果容量超限，计算需要淘汰的key（但保留热key）

**关键特性**：
- 无状态设计：只负责计算和选择，不维护热Key列表
- 纯函数式：根据输入（当前热Key列表、访问统计）计算输出（应该晋升/降级的key）
- 职责单一：只负责选择逻辑，状态管理由HotKeyManager负责

### 4.7 HotKeyMonitor（模块五）

**职责**：
- 每分钟监控并输出：
  - 热key列表和数量
  - 数据存储层大小
  - 访问记录模块数据量和大小

**关键特性**：
- 只读操作（监控），低耦合（通过接口依赖）
- 定期执行监控和清理检查（定时任务）
- 监控缓存状态

### 4.8 CacheDataUpdater（模块六）

**职责**：
- 存储和管理每个Redis key对应的数据获取回调函数
- 定时刷新热Key数据，保证数据新鲜度
- 管理刷新失败计数
- 自动清理失败的key

**关键特性**：
- 高内聚：所有数据刷新相关的功能都集中在此模块（存储回调函数 + 定时刷新）
- 低耦合：通过接口依赖其他模块
- 责任清晰：负责缓存数据的更新（存储回调函数和自动刷新）
- 线程安全：使用ConcurrentHashMap，支持高并发读写
- 高性能：所有操作都是O(1)时间复杂度
- 异常隔离：单个key刷新失败不影响其他key
- 自动清理：刷新失败超过阈值后自动移除

**使用场景**：
- 自动注册：在`HotKeyClient.wrapGet()`方法中，当判断为热Key时自动注册数据获取回调函数
- 自动刷新：定时从注册表获取所有key，调用数据获取回调函数刷新热Key数据
- 自动清理：当热Key被降级或刷新失败时，自动从注册表移除

## 5. 配置说明

### 5.1 热Key检测配置

- `enabled`: 是否启用热Key检测
- `window-size`: 统计窗口大小（秒），默认60
- `top-n`: Top N数量，默认10
- `hot-key-qps-threshold`: 热Key QPS阈值（次/秒），默认3000.0
- `warm-key-qps-threshold`: 温Key QPS阈值（次/秒），默认500.0
- `promotion-interval`: 晋升间隔（毫秒），默认5000（5秒）
- `demotion-interval`: 降级间隔（毫秒），默认60000（60秒）

### 5.2 本地缓存配置

- `enabled`: 是否启用本地缓存，默认true
- `maximum-size`: 最大缓存数量，默认100
- `expire-after-write`: 写入后过期时间（秒），默认60
- `record-stats`: 是否记录统计信息，默认true

### 5.3 访问记录配置

- `max-capacity`: 访问记录最大容量，默认10000
- `window-size`: 统计窗口大小（秒），默认60

### 5.4 自动刷新配置

- `enabled`: 是否启用自动刷新，默认true
- `interval`: 刷新间隔（毫秒），默认10000（10秒）
- `max-failure-count`: 刷新失败重试次数，默认3次

**配置建议**：
- **刷新间隔**：根据数据更新频率调整
  - 数据更新频繁：建议5-10秒
  - 数据更新不频繁：建议30-60秒
- **失败阈值**：根据网络稳定性调整
  - 网络稳定：建议3-5次
  - 网络不稳定：建议5-10次

### 5.5 监控配置

- `interval`: 监控输出间隔（毫秒），默认60000（60秒）

## 6. 关键设计点

### 6.1 热Key判定标准

**热Key判定标准**（必须同时满足）：
1. **QPS阈值**：访问频率 >= 3000次/秒
2. **Top N排名**：访问频率排名在前N位（默认N=10）

**温Key判定标准**：
- QPS >= 500 且 < 3000

**冷Key判定标准**：
- QPS < 500

### 6.2 缓存策略

**缓存时机**：
- 读操作触发：当从Redis获取数据后，如果判断为热Key，自动写入本地缓存
- 写操作触发：当写入Redis后，如果是热Key，异步更新本地缓存

**缓存更新**：
- 热Key写入：当热key被更新时，异步更新本地缓存
- 自动刷新：定时从Redis刷新热Key数据，保证数据新鲜度（默认每10秒）
- 自动过期：Caffeine自动处理过期，无需手动清理

**缓存淘汰**：
- 容量限制：达到最大容量时，使用LRU策略淘汰最少使用的key
- 时间过期：写入后60秒自动过期
- 降级清理：热Key被降级时，从缓存中移除

### 6.3 刷新机制

**刷新策略**：
- 只刷新已注册的key（即热Key）
- 定时刷新：默认每10秒刷新一次
- 异常隔离：单个key刷新失败不影响其他key

**失败处理**：
- 失败计数：使用ConcurrentHashMap记录每个key的连续失败次数
- 自动移除：失败次数超过阈值（默认3次）后，自动移除该热key
- 成功重置：刷新成功时自动清除失败计数

**清理机制**：
- 降级清理：当热Key被降级时，从注册表移除，停止自动刷新
- 失败清理：刷新失败超过阈值后，从缓存、注册表、失败计数中移除

### 6.4 线程安全

**并发安全**：
- 使用ConcurrentHashMap保证线程安全
- 使用volatile保证可见性
- 使用synchronized保证更新操作的原子性
- 使用LongAdder提高并发计数性能

**异步处理**：
- 所有统计和缓存操作都是异步的，不阻塞主流程
- 使用CompletableFuture实现异步处理

## 7. 性能特性

### 7.1 时间复杂度

- 热Key判断：O(1) - 使用Set.contains()
- 缓存获取：O(1) - Caffeine缓存
- 访问记录：O(1) - ConcurrentHashMap
- 刷新操作：O(n) - n为已注册的key数量

### 7.2 空间复杂度

- 本地缓存：O(m) - m为热Key数量（默认最多100个）
- 访问记录：O(n) - n为访问的key数量（默认最多10000个）
- 注册表：O(k) - k为热Key数量

### 7.3 并发性能

- 使用ConcurrentHashMap，支持高并发读写
- 无锁设计，性能优异
- 异步处理，不阻塞主流程

## 8. 使用方式

### 8.1 基本使用

1. 配置热Key功能（在application.yml中）
2. 初始化HotKeyClient并注入到RedisClientManager
3. 使用RedisClientManager（无需修改业务代码）

### 8.2 关键方法

- `wrapGet(String key, Function<String, String> redisGetter)`: 包装get操作，自动处理热Key缓存逻辑
- `recordAccess(String key)`: 记录访问日志
- `getFromCache(String key)`: 从本地缓存获取
- `updateCache(String key, String value)`: 更新本地缓存
- `shutdown()`: 关闭客户端，停止定时任务和刷新服务

## 9. 注意事项

1. **内存管理**：注册表会存储所有热Key的redisGetter，如果热Key数量很大，需要注意内存占用
2. **自动清理**：系统会自动清理降级和失败的key，无需手动干预
3. **刷新频率**：刷新间隔需要根据业务需求调整，过短会增加Redis压力，过长会导致数据不新鲜
4. **失败阈值**：失败阈值需要根据业务场景调整，避免误删正常的key
5. **配置调优**：根据实际业务场景调整各项配置参数，达到最佳性能
