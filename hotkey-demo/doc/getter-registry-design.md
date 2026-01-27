# RedisGetter注册表模块设计文档

## 1. 模块概述

### 1.1 模块名称
`getter` - RedisGetter注册表模块

### 1.2 模块职责
负责存储和管理每个Redis key对应的`Supplier<String> redisGetter`回调函数，供刷新服务等场景复用。

### 1.3 设计目标
- **高内聚**：所有redisGetter相关的存储和管理功能都集中在此模块
- **低耦合**：只依赖Java标准库，不依赖具体业务实现
- **责任清晰**：只负责存储和管理，不涉及业务逻辑
- **依赖明确**：明确依赖关系，避免循环依赖
- **设计优雅**：采用接口+实现模式，支持扩展

## 2. 架构设计

### 2.1 模块结构

```
hotkey/
├── getter/                      # 模块六：RedisGetter注册表
│   ├── IGetterRegistry          # RedisGetter注册表接口
│   └── GetterRegistry           # RedisGetter注册表实现
```

### 2.2 依赖关系

```
GetterRegistry (getter/实现)
    └── 无外部依赖（只依赖Java标准库）

RedisClientManager (demo/)
    └── IGetterRegistry (getter/接口)

HotKeyCacheRefreshService (demo/)
    └── IGetterRegistry (getter/接口)
```

### 2.3 设计原则

1. **单一职责原则**：只负责存储和管理redisGetter
2. **接口隔离原则**：提供简洁的接口，不暴露实现细节
3. **依赖倒置原则**：依赖接口而非实现
4. **开闭原则**：对扩展开放，对修改关闭

## 3. 接口设计

### 3.1 IGetterRegistry 接口

**职责**：定义RedisGetter注册表的标准接口

**方法**：
- `register(String key, Supplier<String> redisGetter)`: 注册redisGetter
- `get(String key)`: 获取指定key的redisGetter
- `remove(String key)`: 移除指定key的redisGetter
- `getAllKeys()`: 获取所有已注册的key
- `clear()`: 清空所有注册的redisGetter
- `size()`: 获取注册表大小
- `contains(String key)`: 检查key是否已注册

## 4. 实现设计

### 4.1 GetterRegistry 实现

**数据结构**：
- 使用`ConcurrentHashMap<String, Supplier<String>>`存储
- 线程安全，支持高并发

**关键特性**：
- 线程安全：使用ConcurrentHashMap
- 高性能：O(1)时间复杂度的查找和插入
- 内存管理：自动清理，支持手动清理

## 5. 使用场景

### 5.1 注册redisGetter

在`RedisClientManager.get()`方法中：
```java
Supplier<String> redisGetter = () -> valueOps.get(key);
getterRegistry.register(key, redisGetter);
```

### 5.2 获取redisGetter

在刷新服务中：
```java
Supplier<String> redisGetter = getterRegistry.get(key);
if (redisGetter != null) {
    String value = redisGetter.get();
}
```

### 5.3 清理redisGetter

在删除key时：
```java
getterRegistry.remove(key);
```

## 6. 扩展性设计

### 6.1 支持自定义实现

通过接口`IGetterRegistry`，可以轻松替换实现：
- 可以基于Redis实现分布式注册表
- 可以基于数据库实现持久化注册表
- 可以添加过期机制、统计功能等

### 6.2 支持监听器模式（可选扩展）

可以添加监听器接口，当注册表发生变化时通知：
```java
interface GetterRegistryListener {
    void onRegister(String key);
    void onRemove(String key);
    void onClear();
}
```

## 7. 性能考虑

### 7.1 时间复杂度
- 注册：O(1)
- 获取：O(1)
- 移除：O(1)
- 获取所有key：O(n)

### 7.2 空间复杂度
- O(n)，n为注册的key数量

### 7.3 并发性能
- 使用ConcurrentHashMap，支持高并发读写
- 无锁设计，性能优异

## 8. 线程安全

### 8.1 并发安全
- 使用`ConcurrentHashMap`保证线程安全
- 所有操作都是原子操作

### 8.2 可见性
- ConcurrentHashMap保证内存可见性
- 无需额外的同步机制

## 9. 错误处理

### 9.1 空值处理
- key为null时，不进行注册
- redisGetter为null时，不进行注册
- 获取时返回null表示未注册

### 9.2 异常处理
- 所有方法都不抛出异常
- 异常情况返回null或false

## 10. 测试考虑

### 10.1 单元测试
- 测试注册、获取、移除功能
- 测试并发场景
- 测试边界情况

### 10.2 集成测试
- 测试与RedisClientManager的集成
- 测试与刷新服务的集成
