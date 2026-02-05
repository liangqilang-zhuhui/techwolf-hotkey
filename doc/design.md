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
- **性能优化**：降低Redis访问压力，提升系统整体性能，优化TP99延迟
- **优雅解耦**：通过HotKeyClient统一处理，实现与业务代码的解耦
- **监控统计**：提供完善的监控和统计功能，包括命中率、QPS、流量占比等

## 2. 整体架构

### 2.1 模块划分

系统采用分层架构，分为6个核心模块：

**模块一：热Key管理器（manager）**
- 职责：记录访问日志、维护和管理热Key列表、判断是否热key、协调各模块
- 接口：`IHotKeyManager`
- 实现：`HotKeyManager`

**模块二：数据存储（storage）**
- 职责：存储热key的value、管理数据获取回调函数注册表、定时刷新热Key数据
- 接口：`IHotKeyStorage`
- 实现：`HotKeyStorage`
- 设计：数据存储模块自己负责定时刷新，属于职责范围内的事情

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
- 职责：监控热key列表、数据存储层大小、访问记录模块数据量、命中率统计
- 接口：`IHotKeyMonitor`
- 实现：`HotKeyMonitor`
- 统计器：`HitRateStatistics` - 统计热Key缓存命中率、QPS、流量占比等

**模块六：缓存数据更新器（updater）**
- 职责：存储和管理每个Redis key对应的数据获取回调函数，并负责自动刷新缓存数据
- 接口：`ICacheDataUpdater`
- 实现：`CacheDataUpdater`
- 说明：此模块的功能已合并到HotKeyStorage中，HotKeyStorage自己负责数据刷新，属于职责范围内的事情

### 2.2 依赖关系

```
HotKeyClient (协调者)
    ├── IHotKeyManager
    │   └── HotKeyManager
    │       ├── IAccessRecorder
    │       ├── IHotKeyStorage
    │       └── (维护 hotKeys 列表)
    ├── IAccessRecorder
    │   └── AccessRecorder
    ├── IHotKeySelector
    │   └── HotKeySelector
    │       └── IAccessRecorder
    ├── IScheduler
    │   └── SchedulerManager (统一调度器)
    │       ├── IHotKeyManager
    │       ├── IHotKeySelector
    │       └── IAccessRecorder
    ├── IHotKeyStorage
    │   └── HotKeyStorage (包含数据存储、注册表管理、定时刷新功能)
    ├── IHitRateStatistics
    │   └── HitRateStatistics
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
2. 记录wrapGet调用统计（用于计算QPS和命中率）
3. 记录访问日志（异步，用于热Key检测）
4. 判断是否为热Key
5. 如果是热Key：
   - 记录热Key访问统计
   - 注册redisGetter到注册表（用于自动刷新，使用putIfAbsent保证原子操作）
   - 先从本地缓存获取值
   - 缓存命中则记录命中统计并直接返回
   - 缓存未命中则记录未命中统计并继续
6. 从Redis获取数据（调用redisGetter）
7. 如果从Redis获取到值，且是热Key，更新本地缓存
8. 返回数据给客户端

**关键点**：
- 只有热Key才走本地缓存
- 非热Key直接走Redis，只异步记录访问统计
- 热Key的redisGetter会自动注册到注册表
- 所有统计和缓存操作都是异步的，不阻塞主流程
- 使用原子操作保证并发安全

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

1. 定时任务启动（每10秒执行一次，可配置）
2. SchedulerManager从HotKeyManager获取当前热Key列表
3. SchedulerManager调用HotKeySelector.promoteHotKeys(currentHotKeys)
4. HotKeySelector计算应该晋升的key：
   - 从AccessRecorder获取所有访问统计
   - 过滤：QPS >= 热Key阈值（默认1000）
   - 排序：按QPS降序
   - 取TopN：limit(topN，默认20)
   - 排除已经是热Key的，返回新晋升的key集合
5. SchedulerManager调用HotKeyManager.promoteHotKeys(newHotKeys)
6. HotKeyManager更新热Key列表（使用synchronized保证原子性，volatile Set保证可见性）
7. 记录日志

**关键点**：
- 必须同时满足QPS阈值和Top N排名
- Selector只负责计算，Manager负责状态管理
- 使用synchronized保证更新操作的原子性
- 使用volatile保证可见性
- **注意**：晋升时只更新热Key列表，数据存储和注册表注册在首次访问时完成（因为需要redisGetter回调函数）

### 3.5 热Key降级流程

**完整流程**：

1. 定时任务启动（每30秒执行一次，可配置）
2. SchedulerManager从HotKeyManager获取当前热Key列表
3. SchedulerManager调用HotKeySelector.demoteAndEvict(currentHotKeys)
4. HotKeySelector计算应该降级的key：
   - 从AccessRecorder获取所有访问统计
   - 检查热key中访问小于阈值的，返回应该移除的key集合
   - 如果容量超限，计算需要淘汰的key（但保留热key）
5. SchedulerManager调用HotKeyManager.demoteHotKeys(removedKeys)
6. HotKeyManager更新热Key列表（使用synchronized保证原子性，volatile Set保证可见性）
7. HotKeyManager调用HotKeyStorage.retainAll()清理被降级key的缓存和注册表
8. 记录日志

**关键点**：
- Selector只负责计算，Manager负责状态管理
- Manager负责热Key的完整生命周期管理，包括降级时的资源清理
- 降级时通过HotKeyStorage.retainAll()自动清理缓存和注册表
- 清理逻辑内聚在Manager中，简化调用链
- **顺序执行**：所有降级相关操作在同一线程中顺序执行，避免竞争条件

### 3.6 自动刷新流程

**完整流程**：

1. 定时任务启动（每10秒执行一次，可配置）
2. **职责内管理**：刷新任务由HotKeyStorage自己管理，属于数据存储模块的职责范围
3. 从HotKeyStorage的注册表获取所有已注册的key
4. 遍历所有已注册的key
5. 对于每个key：
   - 从注册表获取数据获取回调函数
   - 调用数据获取回调函数.apply(key)获取最新值
   - 如果成功，更新本地缓存
   - 如果失败，记录警告日志
6. 记录日志

**关键点**：
- 只刷新已注册的key（即热Key）
- 异常隔离：每个key的刷新使用try-catch隔离，单个key失败不影响其他key
- **职责内管理**：刷新任务由HotKeyStorage自己管理，属于数据存储模块的职责范围
- 刷新任务在HotKeyStorage初始化时启动，使用独立的线程池

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
- **数据完整性检查**：降级前检查数据状态（存储层、注册表），用于监控和排查

**关键特性**：
- 异步记录访问日志，不阻塞主流程
- 维护热Key列表状态，使用volatile Set保证可见性
- 使用synchronized保证更新操作的原子性
- 只处理热Key的缓存操作
- 降级时自动清理资源，保证热Key生命周期的完整性
- **数据完整性保证**：降级前检查，降级时顺序清理，确保资源完整释放

### 4.3 HotKeyStorage（模块二：数据存储）

**职责**：
- 存储热key的value，使用Caffeine本地缓存
- 管理数据获取回调函数注册表（registryUpdateKeys）
- 定时刷新热Key数据，保证数据新鲜度
- 过期时间可配置（默认60分钟）

**关键特性**：
- 使用Caffeine实现高性能本地缓存
- 支持过期时间、容量限制
- 提供统计信息收集
- **数据刷新**：自己负责定时刷新，属于职责范围内的事情
- **注册表管理**：存储每个热Key对应的数据获取回调函数
- **异常隔离**：单个key刷新失败不影响其他key

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
- 自动清理过期和低QPS的key，保留80%的容量

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
  - 数据存储注册表数据量和大小
  - wrapGet总调用次数和QPS
  - 每秒访问的不同key数量
  - 热Key访问总次数
  - 热Key缓存命中次数和未命中次数
  - 热Key命中率和流量占比

**关键特性**：
- 只读操作（监控），低耦合（通过接口依赖）
- 定期执行监控和清理检查（定时任务）
- 监控缓存状态和命中率统计
- 使用DecimalFormat格式化数字输出

### 4.8 HitRateStatistics（命中率统计器）

**职责**：
- 统计wrapGet的流量QPS
- 统计每秒访问的不同key数量
- 统计热key命中数量、未命中数量
- 计算热key命中率和流量占比

**关键特性**：
- 使用原子类（LongAdder）保证线程安全
- 使用时间窗口（10秒）统计QPS
- 使用CAS操作替代同步块，避免高并发下的阻塞
- 内存优化：限制windowKeys最大大小（100000），定期清理过期key

**性能优化**：
- 窗口重置采用无锁算法，只有真正需要重置的线程才执行重置
- 其他线程在重置期间可以继续执行，不会被阻塞
- 定期清理过期key（每5秒），防止内存泄漏
- 超过限制时拒绝新key，保护系统稳定性

### 4.9 CacheDataUpdater（模块六）

**说明**：
- 此模块的功能已合并到HotKeyStorage中
- HotKeyStorage自己负责数据刷新，属于职责范围内的事情
- 不再需要独立的CacheDataUpdater模块
- 刷新任务由HotKeyStorage内部的定时任务管理

### 4.10 SchedulerManager（统一调度器）

**职责**：
- 统一管理定时任务（晋升、降级）
- 顺序执行：关键操作顺序执行，保证原子性和一致性
- 异常处理：统一处理所有定时任务的异常

**关键特性**：
- **职责分明**：每个任务职责清晰，互不干扰
- **异常隔离**：单个任务失败不影响其他任务
- **资源管理**：统一管理线程池的启动和停止

**说明**：
- 刷新任务由HotKeyStorage自己管理，属于数据存储模块的职责范围
- 晋升和降级任务由SchedulerManager统一调度

## 5. 配置说明

### 5.1 热Key检测配置

- `enabled`: 是否启用热Key检测
- `window-size`: 统计窗口大小（秒），默认10
- `top-n`: Top N数量，默认10
- `hot-key-qps-threshold`: 热Key QPS阈值（次/秒），默认3000.0
- `warm-key-qps-threshold`: 温Key QPS阈值（次/秒），默认500.0
- `promotion-interval`: 晋升间隔（毫秒），默认5000（5秒）
- `demotion-interval`: 降级间隔（毫秒），默认60000（60秒）

### 5.2 本地缓存配置

- `enabled`: 是否启用本地缓存，默认true
- `maximum-size`: 最大缓存数量，默认200
- `expire-after-write`: 写入后过期时间（分钟），默认60
- `record-stats`: 是否记录统计信息，默认true

### 5.3 访问记录配置

- `max-capacity`: 访问记录最大容量，默认100000
- `window-size`: 统计窗口大小（秒），默认10
- `inactive-expire-time`: 非活跃key过期时间（秒），默认300

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

- `enabled`: 是否启用监控，默认true
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
- 异常隔离：单个key刷新失败不影响其他key，只记录警告日志

**清理机制**：
- 降级清理：当热Key被降级时，通过HotKeyStorage.retainAll()从缓存和注册表移除

### 6.4 线程安全

**并发安全**：
- 使用ConcurrentHashMap保证线程安全
- 使用volatile保证可见性
- 使用synchronized保证更新操作的原子性
- 使用LongAdder提高并发计数性能
- 使用CAS操作替代同步块，避免高并发下的阻塞

**异步处理**：
- 所有统计和缓存操作都是异步的，不阻塞主流程
- 使用线程池实现异步处理

### 6.5 性能优化

**CAS无锁算法**：
- 窗口重置采用CAS操作，只有第一个线程能成功执行重置
- 其他线程在重置期间可以继续执行，不会被阻塞
- 避免高并发下的阻塞，提升TP99性能

**原子操作优化**：
- 使用 `putIfAbsent` 替代 `containsKey + put`，保证原子操作
- 避免并发场景下的竞态条件

**内存优化**：
- 限制 `windowKeys` 最大大小（100000），防止OOM
- 定期清理过期key（每5秒），防止内存泄漏
- 超过限制时拒绝新key，保护系统稳定性

### 6.6 线程管理与数据完整性保证

**线程合并优化**：
- **统一调度**：所有定时任务（晋升、降级、刷新）统一由SchedulerManager管理
- **单线程池**：使用同一个ScheduledExecutorService，避免多线程竞争
- **顺序执行**：关键操作（晋升、降级、刷新）在同一线程中顺序执行，保证原子性
- **职责分明**：每个任务职责清晰，互不干扰

**数据完整性保证**：
- **晋升时**：
  - 更新热Key列表（立即生效）
  - 数据存储和注册表注册在首次访问时完成（需要redisGetter回调函数）
- **降级前检查**：
  - 检查存储层数据状态（可选，用于监控）
  - 检查注册表注册状态（可选，用于监控）
  - 记录数据完整性信息，便于排查问题
- **降级时清理**：
  - 顺序执行：从热Key列表移除 → 清理存储层数据 → 清理注册表 → 清理失败计数
  - 使用synchronized保证操作的原子性
  - 异常隔离：单个key清理失败不影响其他key

**线程架构**：
```
SchedulerManager (统一调度器)
├── HotKey-Scheduler 线程池（3个线程）
│   ├── 晋升任务（10秒间隔）
│   └── 降级任务（30秒间隔）
└── 顺序执行，避免竞争

HotKeyStorage (数据存储)
├── HotKey-Storage-Updater-Scheduler 线程池（3个线程）
│   └── 刷新任务（10秒间隔）
└── 独立管理，职责清晰
```

**设计说明**：
- SchedulerManager：管理晋升、降级任务
- HotKeyStorage：自己管理刷新任务，属于数据存储模块的职责范围
- 职责分离：数据存储模块负责数据的存储和刷新，调度器负责热Key的晋升和降级

**关键原则**：
- **单一职责**：每个模块职责清晰，不互相干扰
- **顺序执行**：关键操作顺序执行，避免竞争条件
- **数据完整性**：降级前检查，降级时清理，确保资源完整释放
- **异常隔离**：单个操作失败不影响其他操作

## 7. 性能特性

### 7.1 时间复杂度

- 热Key判断：O(1) - 使用Set.contains()
- 缓存获取：O(1) - Caffeine缓存
- 访问记录：O(1) - ConcurrentHashMap
- 刷新操作：O(n) - n为已注册的key数量

### 7.2 空间复杂度

- 本地缓存：O(m) - m为热Key数量（默认最多200个）
- 访问记录：O(n) - n为访问的key数量（默认最多100000个）
- 注册表：O(k) - k为热Key数量
- 命中率统计：O(w) - w为窗口内的key数量（最多100000个）

### 7.3 并发性能

- 使用ConcurrentHashMap，支持高并发读写
- CAS无锁设计，性能优异，避免阻塞
- 异步处理，不阻塞主流程
- 优化TP99延迟，提升高并发场景下的性能

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

## 9. 设计优化总结

### 9.1 职责分离设计

**设计说明**：
- SchedulerManager：管理晋升、降级任务，负责热Key列表的更新
- HotKeyStorage：自己管理刷新任务，负责数据的存储和刷新
- 职责分离：数据存储模块负责数据的存储和刷新，调度器负责热Key的晋升和降级
- 优势：
  - 职责清晰，每个模块管理自己的任务
  - 低耦合，模块之间互不干扰
  - 便于维护，职责边界明确

### 9.2 数据完整性保证

**优化内容**：
1. **降级前检查**：
   - 检查存储层数据状态
   - 检查注册表注册状态
   - 记录数据完整性信息，用于监控和排查

2. **降级时顺序清理**：
   - 从热Key列表移除
   - 清理存储层数据
   - 清理注册表
   - 清理失败计数
   - 使用synchronized保证原子性

3. **晋升时说明**：
   - 晋升时只更新热Key列表
   - 数据存储和注册表注册在首次访问时完成（需要redisGetter回调函数）

### 9.3 QPS计算优化

**优化前**：
- QPS = (count * 1000.0) / elapsed
- 问题：窗口刚重置后短时间内大量访问会导致QPS虚高

**优化后**：
- QPS = count / (double) windowSize
- 优势：使用窗口大小计算QPS，平滑QPS计算，避免突发访问导致的误判

## 10. 注意事项

1. **内存管理**：
   - 注册表会存储所有热Key的redisGetter，如果热Key数量很大，需要注意内存占用
   - 命中率统计模块会限制windowKeys最大大小（100000），防止OOM
   - 系统会自动清理降级和失败的key，无需手动干预

2. **自动清理**：
   - 系统会自动清理降级和失败的key，无需手动干预
   - 访问记录模块会自动清理过期和低QPS的key，保留80%的容量
   - 命中率统计模块会定期清理过期key（每5秒）

3. **刷新频率**：
   - 刷新间隔需要根据业务需求调整，过短会增加Redis压力，过长会导致数据不新鲜
   - **重要**：刷新间隔应小于`expire-after-write`，确保在缓存过期前刷新

4. **配置调优**：
   - 根据实际业务场景调整各项配置参数，达到最佳性能

6. **性能优化**：
   - 系统已实现多项性能优化（CAS无锁算法、原子操作优化、内存优化）
   - 在高并发场景下，TP99延迟得到显著优化

7. **数据完整性保证**：
   - **晋升时**：热Key列表立即更新，但数据存储和注册表注册在首次访问时完成（需要redisGetter）
   - **降级时**：顺序执行清理操作（热Key列表 → 存储层 → 注册表），保证完整性

8. **线程管理**：
   - **职责分离**：晋升、降级任务由SchedulerManager管理，刷新任务由HotKeyStorage自己管理
   - **顺序执行**：关键操作在同一线程中顺序执行，保证原子性
   - **职责分明**：每个模块职责清晰，互不干扰
   - **异常隔离**：单个任务失败不影响其他任务

9. **缓存过期时间配置**：
   - **重要**：`expire-after-write`（单位：分钟）应大于`refresh.interval`（单位：毫秒），确保在过期前刷新
   - 建议：`expire-after-write`（分钟） >= `refresh.interval`（毫秒） / 60000 * 2，提供安全余量
   - 示例：刷新间隔10秒（10000毫秒），过期时间建议至少20分钟，推荐60分钟
