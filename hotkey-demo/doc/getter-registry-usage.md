# RedisGetter注册表模块使用指南

## 1. 模块概述

`getter`模块是热Key系统的第六个核心模块，负责存储和管理每个Redis key对应的`Supplier<String> redisGetter`回调函数。

## 2. 设计特点

### 2.1 高内聚
- 所有redisGetter相关的存储和管理功能都集中在此模块
- 单一职责：只负责存储和管理redisGetter

### 2.2 低耦合
- 只依赖Java标准库（`java.util.concurrent`、`java.util.function`）
- 不依赖具体业务实现
- 通过接口定义，支持多种实现

### 2.3 责任清晰
- 只负责存储和管理，不涉及业务逻辑
- 不关心redisGetter的具体实现
- 不关心key的业务含义

### 2.4 依赖明确
- 无外部依赖（只依赖Java标准库）
- 被`RedisClientManager`和`HotKeyCacheRefreshService`依赖
- 依赖关系清晰，无循环依赖

## 3. 核心接口

### 3.1 IGetterRegistry

```java
public interface IGetterRegistry {
    // 注册redisGetter
    void register(String key, Supplier<String> redisGetter);
    
    // 获取redisGetter
    Supplier<String> get(String key);
    
    // 移除redisGetter
    void remove(String key);
    
    // 获取所有已注册的key
    Set<String> getAllKeys();
    
    // 清空所有注册的redisGetter
    void clear();
    
    // 获取注册表大小
    int size();
    
    // 检查key是否已注册
    boolean contains(String key);
}
```

## 4. 使用示例

### 4.1 基本使用

```java
// 创建注册表实例
IGetterRegistry registry = new GetterRegistry();

// 注册redisGetter
Supplier<String> redisGetter = () -> redisTemplate.opsForValue().get("user:123");
registry.register("user:123", redisGetter);

// 获取redisGetter
Supplier<String> getter = registry.get("user:123");
if (getter != null) {
    String value = getter.get();
}

// 检查是否已注册
boolean exists = registry.contains("user:123");

// 移除redisGetter
registry.remove("user:123");

// 获取所有key
Set<String> allKeys = registry.getAllKeys();

// 获取注册表大小
int size = registry.size();

// 清空所有
registry.clear();
```

### 4.2 在RedisClientManager中使用

```java
public class RedisClientManager {
    private final IGetterRegistry getterRegistry = new GetterRegistry();
    
    public String get(String key) {
        if (hotKeyClient != null && hotKeyClient.isEnabled()) {
            // 创建redisGetter
            Supplier<String> redisGetter = () -> valueOps.get(key);
            
            // 注册redisGetter供刷新服务复用
            getterRegistry.register(key, redisGetter);
            
            // 使用redisGetter
            return hotKeyClient.wrapGet(key, redisGetter);
        }
        return valueOps.get(key);
    }
    
    public boolean del(String key) {
        boolean result = redisTemplate.delete(key);
        // 清理注册表
        getterRegistry.remove(key);
        return result;
    }
}
```

### 4.3 在刷新服务中使用

```java
@Service
public class HotKeyCacheRefreshService {
    @Autowired
    private RedisClientManager redisClientManager;
    
    @Scheduled(fixedDelay = 10000)
    public void refreshHotKeyCache() {
        // 获取注册表
        IGetterRegistry registry = redisClientManager.getGetterRegistry();
        
        // 获取热Key列表
        Set<String> hotKeys = getHotKeys();
        
        for (String key : hotKeys) {
            // 从注册表获取redisGetter
            Supplier<String> redisGetter = registry.get(key);
            if (redisGetter != null) {
                // 使用redisGetter刷新缓存
                String value = redisGetter.get();
                hotKeyClient.updateCache(key, value);
            }
        }
    }
}
```

## 5. 性能特性

### 5.1 时间复杂度
- 注册：O(1)
- 获取：O(1)
- 移除：O(1)
- 获取所有key：O(n)
- 检查是否存在：O(1)

### 5.2 空间复杂度
- O(n)，n为注册的key数量

### 5.3 并发性能
- 使用`ConcurrentHashMap`实现，支持高并发读写
- 无锁设计，性能优异
- 所有操作都是线程安全的

## 6. 线程安全

### 6.1 并发安全
- 使用`ConcurrentHashMap`保证线程安全
- 所有操作都是原子操作
- 支持多线程并发读写

### 6.2 可见性
- `ConcurrentHashMap`保证内存可见性
- 无需额外的同步机制

## 7. 错误处理

### 7.1 空值处理
- key为null时，不进行注册（防御性编程）
- redisGetter为null时，不进行注册
- 获取时返回null表示未注册

### 7.2 异常处理
- 所有方法都不抛出异常
- 异常情况返回null或false
- 保证调用方的稳定性

## 8. 最佳实践

### 8.1 注册时机
- 在`wrapGet`时注册，确保每个被访问的key都有对应的redisGetter
- 避免过早注册，只在需要时注册

### 8.2 清理时机
- 在删除key时清理，避免内存泄漏
- 在应用关闭时清空所有注册表

### 8.3 使用建议
- 优先使用接口`IGetterRegistry`，而不是具体实现
- 避免直接操作内部数据结构
- 定期检查注册表大小，避免内存占用过大

## 9. 扩展性

### 9.1 支持自定义实现
通过接口`IGetterRegistry`，可以轻松替换实现：
- 可以基于Redis实现分布式注册表
- 可以基于数据库实现持久化注册表
- 可以添加过期机制、统计功能等

### 9.2 支持监听器模式（可选扩展）
可以添加监听器接口，当注册表发生变化时通知：
```java
interface GetterRegistryListener {
    void onRegister(String key);
    void onRemove(String key);
    void onClear();
}
```

## 10. 注意事项

1. **内存管理**：注册表会占用内存，需要定期清理不用的key
2. **线程安全**：虽然实现是线程安全的，但需要注意redisGetter本身的线程安全
3. **性能考虑**：注册表操作都是O(1)，但在高并发场景下仍需注意性能
4. **生命周期**：redisGetter的生命周期应该与key的生命周期一致
