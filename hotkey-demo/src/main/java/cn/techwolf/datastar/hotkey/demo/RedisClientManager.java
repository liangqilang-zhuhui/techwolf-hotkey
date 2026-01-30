package cn.techwolf.datastar.hotkey.demo;

import cn.techwolf.datastar.hotkey.IHotKeyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class RedisClientManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisClientManager.class);

    public final static String REDIS_ERROR_COUNT = "redis.error.count";
    
    /**
     * Redis模板，由Spring自动注入
     */
    @Autowired
    private StringRedisTemplate redisTemplate;

    private ValueOperations<String, String> valueOps;

    private HashOperations<String, String, String> hashOps;

    private ListOperations<String, String> listOps;

    private SetOperations<String, String> setOps;

    private ZSetOperations<String, String> zsetOps;


    private HyperLogLogOperations<String, String> hyperLogLogOps;

    private Boolean errorMonitor;

    /**
     * 热Key客户端（可选，通过setter注入）
     * 用于提供热Key缓存服务，优化get操作的性能
     */
    @Autowired(required = false)
    private IHotKeyClient hotKeyClient;

    public RedisClientManager(){

    }
    
    /**
     * 初始化Redis操作对象
     * 在Spring容器创建Bean后自动调用，确保所有操作对象都被正确初始化
     *
     * @author techwolf
     * @date 2024
     */
    @PostConstruct
    public void init() {
        if (redisTemplate != null) {
            this.valueOps = redisTemplate.opsForValue();
            this.hashOps = redisTemplate.opsForHash();
            this.listOps = redisTemplate.opsForList();
            this.setOps = redisTemplate.opsForSet();
            this.zsetOps = redisTemplate.opsForZSet();
            this.hyperLogLogOps = redisTemplate.opsForHyperLogLog();
            LOGGER.info("RedisClientManager初始化完成");
        } else {
            LOGGER.warn("StringRedisTemplate未注入，RedisClientManager可能无法正常工作");
        }
    }
    public RedisClientManager(StringRedisTemplate redisTemplate, Boolean errorMonitor) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
        this.hashOps = redisTemplate.opsForHash();
        this.listOps = redisTemplate.opsForList();
        this.setOps = redisTemplate.opsForSet();
        this.zsetOps = redisTemplate.opsForZSet();
        this.hyperLogLogOps = redisTemplate.opsForHyperLogLog();
        this.errorMonitor = errorMonitor;
    }

    public RedisClientManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
        this.hashOps = redisTemplate.opsForHash();
        this.listOps = redisTemplate.opsForList();
        this.setOps = redisTemplate.opsForSet();
        this.zsetOps = redisTemplate.opsForZSet();
        this.hyperLogLogOps = redisTemplate.opsForHyperLogLog();
    }

    public void setRedisClientManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
        this.hashOps = redisTemplate.opsForHash();
        this.listOps = redisTemplate.opsForList();
        this.setOps = redisTemplate.opsForSet();
        this.zsetOps = redisTemplate.opsForZSet();
        this.hyperLogLogOps = redisTemplate.opsForHyperLogLog();
    }

    /**
     * 设置热Key客户端
     * 用于提供热Key缓存服务，优化get操作的性能
     *
     * @param hotKeyClient 热Key客户端
     */
    public void setHotKeyClient(IHotKeyClient hotKeyClient) {
        this.hotKeyClient = hotKeyClient;
    }

    private void redisExceptionMonitor(Throwable e, String methodName) {
        try {
            if (Objects.isNull(errorMonitor)) {
                return;
            }
            if (!errorMonitor) {
                return;
            }
        } catch (Throwable throwable) {
            LOGGER.error("redisExceptionMonitor 错误", throwable);
        }
    }

    public boolean exists(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "exists");
            throw new RedisClientRuntimeException(e);
        }

    }

    /**
     * 设置值
     *
     * @param key    键
     * @param offset 偏移量
     * @param value  boolean类型
     */
    public void setBit(String key, Integer offset, Boolean value) {
        try {
            valueOps.setBit(key, offset, value);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "setBit");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 通过键获取字符串形式的值。
     *
     * @param key 键。
     * @return key对应的值。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public Boolean getBit(String key, Integer offset) {
        try {
            return valueOps.getBit(key, offset);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "getBit");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 设置值。
     * 业务逻辑：
     * 1. 先写入Redis（同步，保证数据一致性）
     * 2. 判断热Key功能是否启用
     * 3. 如果是热Key，异步更新本地缓存
     * 4. 异步记录访问统计
     *
     * @param key   键。
     * @param value 字符串形式的值。
     * @throws RedisClientRuntimeException 如果设置不成功或者发生异常等。
     */
    public void set(String key, String value) {
        try {
            valueOps.set(key, value);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "set");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 设置值。
     *
     * @param key      键。
     * @param value    字符串形式的值。
     * @param expire   失效时间(秒)。
     * @param timeUnit 时间颗粒度
     * @throws RedisClientRuntimeException 如果设置不成功或者发生异常等。
     */
    public void set(String key, String value, int expire, TimeUnit timeUnit) {
        try {
            valueOps.set(key, value, expire, timeUnit);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "set");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 设置值。
     *
     * @param key    键。
     * @param value  字符串形式的值。
     * @param expire 失效时间(秒)。
     * @throws RedisClientRuntimeException 如果设置不成功或者发生异常等。
     */
    public void set(String key, String value, int expire) {
        try {
            valueOps.set(key, value, expire, TimeUnit.SECONDS);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "set");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 如果key不存在，设置值。
     *
     * @param key   键。
     * @param value 字符串形式的值。
     * @return 设置成功返回true；未设置返回false；
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public boolean setnx(String key, String value) {
        try {
            return valueOps.setIfAbsent(key, value);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "setnx");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 批量设置值。
     *
     * @param map 包含键值对的map。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public void mset(Map<String, String> map) {
        try {
            valueOps.multiSet(map);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "mset");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 设置对象。
     *
     * @param key   键。
     * @param value 对象，内部会转成json格式的字符串。
     * @throws RedisClientRuntimeException 如果设置不成功或者发生异常等。
     */
    public void set(String key, Object value) {
        try {
            String json = ProtoBufCompatibleJsonUtils.toJson(value);
            valueOps.set(key, json);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "set");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 设置对象。
     *
     * @param key    键。
     * @param value  对象，内部会转成json格式的字符串。
     * @param expire 失效时间(秒)。
     * @throws RedisClientRuntimeException 如果设置不成功或者发生异常等。
     */
    public void set(String key, Object value, int expire) {
        try {
            String json = ProtoBufCompatibleJsonUtils.toJson(value);
            valueOps.set(key, json, expire, TimeUnit.SECONDS);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "set");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 如果key不存在，设置对象。
     *
     * @param key   键。
     * @param value 对象，内部会转成json格式的字符串。
     * @return 设置成功返回true；未设置返回false；
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public boolean setnx(String key, Object value) {
        try {
            String json = ProtoBufCompatibleJsonUtils.toJson(value);
            return valueOps.setIfAbsent(key, json);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "setnx");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 通过键获取字符串形式的值。
     * 业务逻辑：
     * 1. 记录访问日志（用于热Key检测）
     * 2. 如果是热Key，先从本地缓存获取值
     * 3. 如果缓存未命中，从Redis获取值，并更新本地缓存
     *
     * @param key 键。
     * @return key对应的值。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public String get(String key) {
        try {
            if (hotKeyClient != null && hotKeyClient.isEnabled()) {
                // 使用回调函数，一行代码搞定所有热Key逻辑
                return hotKeyClient.wrapGet(key, k -> valueOps.get(k));
            }
            // 如果热Key客户端未启用，直接获取
            return valueOps.get(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "get");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 批量通过键获取值的集合。
     *
     * @param keys 键的集合。
     * @return 对应的值的集合。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public List<String> mget(List<String> keys) {
        try {
            return valueOps.multiGet(keys);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "mget");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 批量通过键获取值的集合。
     *
     * @param keys 键数组。
     * @return 对应的值的集合。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public List<String> mget(String... keys) {
        try {
            return valueOps.multiGet(Arrays.asList(keys));
        } catch (Throwable e) {
            redisExceptionMonitor(e, "mget");
            throw new RedisClientRuntimeException(e);
        }
    }



    /**
     * 通过键删除对应的值。
     * 业务逻辑：
     * 1. 删除Redis中的key（同步，保证数据一致性）
     * 2. 判断热Key功能是否启用
     * 3. 如果是热Key，异步从缓存中删除
     *
     * @param key 键。
     * @return 删除成功返回true，否则返回false
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public boolean del(String key) {
        try {
            // 1. 先删除Redis中的key（同步，保证数据一致性）
            return redisTemplate.delete(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "del");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 通过键删除对应的值。
     *
     * @param keys 键。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long del(Collection keys) {
        try {
            return redisTemplate.delete(keys);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "del");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 递增指定键对应的数值。
     * <p>如果不存在key对应的值，那么会先将key的值设置为0，
     * 然后执行incr操作，返回1。
     *
     * @param key 键。
     * @return 递增后key对应的值。
     * @throws RedisClientRuntimeException 如果key对应的值不是数值
     *                                     或者key对应的数值越界或者发生异常等。
     */
    public long incr(String key) {
        try {
            return valueOps.increment(key, 1);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "incr");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 将指定键对应的数值增加给定的增量。
     * <p>如果不存在key对应的值，那么会先将key的值设置为0，
     * 然后执行incrBy操作，返回increment。
     *
     * @param key       键。
     * @param increment 增量值。
     * @return 递增后key对应的值。
     * @throws RedisClientRuntimeException 如果key对应的值不是数值
     *                                     或者key对应的数值越界或者发生异常等。
     */
    public long incrBy(String key, long increment) {
        try {
            return valueOps.increment(key, increment);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "incrBy");
            throw new RedisClientRuntimeException(e);

        }
    }

    public double incrBy(String key, double increment) {
        try {
            return valueOps.increment(key, increment);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "incrBy");
            throw new RedisClientRuntimeException(e);

        }
    }

    /**
     * 给指定的键设置超时时间。
     *
     * @param key     键。
     * @param seconds 超时时间(单位:秒)。
     * @return 如果成功设置超时，返回true；
     * 如果key不存在或者未成功设置超时，返回false。
     */
    public boolean expire(String key, int seconds) {
        try {
            return redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "expire");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 给指定的键设置超时时间。
     *
     * @param key     键。
     * @param seconds 超时时间(单位:秒)。
     * @param unit    时间单位
     * @return 如果成功设置超时，返回true；
     * 如果key不存在或者未成功设置超时，返回false。
     */
    public boolean expire(String key, int seconds, TimeUnit unit) {
        try {
            return redisTemplate.expire(key, seconds, unit);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "expire");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取指定键的剩余存活时间。
     *
     * @param key 键。
     * @return 返回剩余存活时间，如果key为持久化key，返回-1；如果key不存在，返回-2；
     */
    public long ttl(String key) {
        try {
            return redisTemplate.getExpire(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "ttl");
            throw new RedisClientRuntimeException(e);
        }
    }


    /*
     * 哈希表相关操作====================================
     */

    /**
     * 为键对应的哈希表设置一个字段及对应的值。
     *
     * @param key   哈希表在redis中的key。
     * @param field 哈希表中的字段。
     * @param value field对应的字符串形式的值。
     * @return 如果field是哈希表中的一个新字段，返回1；
     * 如果field已经在哈希表中，值被更新，返回0；
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public void hset(String key, String field, String value) {
        try {
            hashOps.put(key, field, value);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hset");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 为键对应的哈希表设置多个字段及对应的值。
     *
     * @param key 哈希表在redis中的key。
     * @param map 多个字段及对应值组成的哈希表。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public void hmset(String key, Map<String, String> map) {
        try {
            hashOps.putAll(key, map);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hmset");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 从键对应的哈希表中获取给定字段对应的值。
     *
     * @param key   哈希表在redis中的key。
     * @param field 哈希表中的字段。
     * @return 哈希表中field对应的字符串形式的值。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public String hget(String key, String field) {
        try {
            return hashOps.get(key, field);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hget");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 从键对应的哈希表中获取给定一批字段对应的值。
     *
     * @param key    哈希表在redis中的key。
     * @param fields 哈希表中的一批字段。
     * @return 哈希表中fields对应的字符串形式的值的集合。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public List<String> hmget(String key, String... fields) {
        try {
            return hashOps.multiGet(key, Arrays.asList(fields));
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hmget");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取键对应的哈希表。
     *
     * @param key 哈希表在redis中的key。
     * @return key对应的哈希表。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public Map<String, String> hgetAll(String key) {
        try {
            return hashOps.entries(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hgetAll");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 从键对应的哈希表中删除给定字段对应的值。
     *
     * @param key    键。
     * @param fields 哈希表中的字段。
     * @return 返回从哈希表中删除字段的数量。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long hdel(String key, Object... fields) {
        try {
            return hashOps.delete(key, fields);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hdel");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 判断键对应的哈希表中是否存在给定字段及对应的值。
     *
     * @param key   键。
     * @param field 哈希表中的字段。
     * @return 如果存在，返回true；不存在返回false；
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public boolean hexists(String key, String field) {
        try {
            return hashOps.hasKey(key, field);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hexists");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取给定键对应的哈希表中所有字段。
     *
     * @param key 键。
     * @return key对应的哈希表的所有字段的集合。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public Set<String> keys(String key) {
        try {
            return redisTemplate.keys(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "keys");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取给定键对应的哈希表中所有字段。
     *
     * @param key 键。
     * @return key对应的哈希表的所有字段的集合。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public Set<String> hkeys(String key) {
        try {
            return hashOps.keys(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hkeys");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取给定键对应的哈希表中的字段数量。
     *
     * @param key 键。
     * @return key对应的哈希表的字段数量。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long hlen(String key) {
        try {
            return hashOps.size(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hlen");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 将给定键对应的哈希表中的给定字段数值累加1。
     *
     * @param key   键。
     * @param field 哈希表中的字段。
     * @return 累加后字段的值。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long hincr(String key, String field) {
        try {
            return hashOps.increment(key, field, 1);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hincr");
            throw new RedisClientRuntimeException(e);
        }
    }

    public long hincrBy(String key, String field, long n) {
        try {
            return hashOps.increment(key, field, n);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hincrBy");
            throw new RedisClientRuntimeException(e);
        }
    }

    public double hincrBy(String key, String field, double n) {
        try {
            return hashOps.increment(key, field, n);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "hincrBy");
            throw new RedisClientRuntimeException(e);
        }
    }

    /*
     * 列表相关操作=================================
     */

    /**
     * 获取给定键对应的列表的指定下标的字符串形式的值。
     *
     * @param key   键。
     * @param index 下标。
     * @return 如果指定下标存在值，返回该值；否则返回null；
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public String lindex(String key, long index) {
        try {
            return listOps.index(key, index);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "lindex");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 在给定键对应的列表头部插入一个或多个字符串形式的值。
     *
     * @param key    键。
     * @param values 要插入链表的值。
     * @return 返回链表的长度。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long lpush(String key, String... values) {
        try {
            return listOps.leftPushAll(key, values);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "lpush");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取给定键对应的列表头部的元素值。
     *
     * @param key 键。
     * @return 链表头部的字符串形式的值。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public String lpop(String key) {
        try {
            return listOps.leftPop(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "lpop");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取给定键对应列表中的第一个字符串形式的元素，或者阻塞直到有可用元素或者超时。
     *
     * @param key     键。
     * @param timeout 超时时间(秒)。
     * @return 列表中的第一个元素；如果超时返回null。
     */
    public String blpop(String key, int timeout) {
        try {
            return listOps.leftPop(key, timeout, TimeUnit.SECONDS);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "blpop");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 在给定键对应的列表尾部插入一个或多个字符串形式的值。
     *
     * @param key    键。
     * @param values 要插入链表的值。
     * @return 返回链表的长度。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long rpush(String key, String... values) {
        try {
            return listOps.rightPushAll(key, values);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "rpush");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取给定键对应的列表尾部的元素值。
     *
     * @param key 键。
     * @return 链表尾部的字符串形式的值。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public String rpop(String key) {
        try {
            return listOps.rightPop(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "rpop");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取给定键对应列表中的最后一个字符串形式的元素，或者阻塞直到有可用元素或者超时。
     *
     * @param key     键。
     * @param timeout 超时时间(秒)。
     * @return 列表中的第一个元素；如果超时返回null。
     */
    public String brpop(String key, int timeout) {
        try {
            return listOps.rightPop(key, timeout, TimeUnit.SECONDS);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "brpop");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取给定键对应的列表长度。
     *
     * @param key 键。
     * @return 如果存在列表，返回列表长度；否则返回0；
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long llen(String key) {
        try {
            return listOps.size(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "llen");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取给定键对应的列表的指定范围的字符串形式的元素。
     *
     * @param key   键。
     * @param start 起始范围。
     * @param end   结束返回。
     * @return 指定范围内的元素集合。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public List<String> lrange(String key, long start, long end) {
        try {
            return listOps.range(key, start, end);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "lrange");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 从源列表的尾部弹出一个元素，并将该元素推入目标列表的头部。
     * 这是一个原子操作。
     *
     * @param sourceKey      源列表的键。
     * @param destinationKey 目标列表的键。
     * @return 被弹出和推入的元素；如果源列表为空，返回null。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public String rpoplpush(String sourceKey, String destinationKey) {
        try {
            return listOps.rightPopAndLeftPush(sourceKey, destinationKey);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "rpoplpush");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 从列表中删除指定数量的值等于给定值的元素。
     *
     * @param key   键。
     * @param count 要删除的元素数量。如果count > 0，从头部开始删除；
     *              如果count < 0，从尾部开始删除；如果count = 0，删除所有匹配的元素。
     * @param value 要删除的元素值。
     * @return 实际删除的元素数量。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long lrem(String key, long count, String value) {
        try {
            return listOps.remove(key, count, value);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "lrem");
            throw new RedisClientRuntimeException(e);
        }
    }

    /*
     * 集合相关操作=================================
     */

    /**
     * 为给定键对应的集合中添加一个或多个字符串形式的元素。
     *
     * @param key    键。
     * @param values 要添加的元素。
     * @return 成功添加的元素数量。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long sadd(String key, String... values) {
        try {
            return setOps.add(key, values);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "sadd");
            throw new RedisClientRuntimeException(e);
        }
    }

    public List<String> pop(String key, long num) {
        try {
            return setOps.pop(key, num);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "pop");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 删除给定键对应的集合元素。
     *
     * @param key    键。
     * @param values 要删除的元素。
     * @return 成功删除的元素数量。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long srem(String key, Object... values) {
        try {
            return setOps.remove(key, values);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "srem");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取给定键对应的集合元素。
     *
     * @param key 键。
     * @return 集合中所有元素
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public Set<String> smembers(String key) {
        try {
            return setOps.members(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "smembers");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 判断给定元素是否在给定键对应的集合中。
     *
     * @param key   键。
     * @param value 元素。
     * @return 如果value是key对应集合中的元素，返回true；否则返回false。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public boolean sismember(String key, String value) {
        try {
            return setOps.isMember(key, value);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "sismember");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取指定集合中的元素数量。
     *
     * @param key 键。
     * @return 集合中的元素数量，当集合不存在时，返回0。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long scard(String key) {
        try {
            return setOps.size(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "scard");
            throw new RedisClientRuntimeException(e);
        }
    }

    public String randomMember(String key) {
        try {
            return setOps.randomMember(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "randomMember");
            throw new RedisClientRuntimeException(e);
        }
    }

    public Set<String> distinctRandomMembers(String key, int n) {
        try {
            return setOps.distinctRandomMembers(key, n);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "distinctRandomMembers");
            throw new RedisClientRuntimeException(e);
        }
    }

    public List<String> srandmembers(String key, int n) {
        try {
            return setOps.randomMembers(key, n);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "srandmembers");
            throw new RedisClientRuntimeException(e);
        }
    }

    /*
     * 有序集合相关操作=================================
     */

    /**
     * 给指定键对应的有序集合添加元素。
     *
     * @param key   键。
     * @param value 字符串形式的元素。
     * @param score 元素的分数。
     * @return 如果集合中不存在value，成功插入集合，返回true。
     * 如果集合中已存在value，更新集合元素，返回false。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public boolean zadd(String key, String value, double score) {
        try {
            return zsetOps.add(key, value, score);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "zadd");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 给指定键对应的有序集合添加元素。
     *
     * @param key 键。
     * @param map 要插入的元素和分数对。
     * @return 成功插入数量。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long zadd(String key, Map<String, Double> map) {
        try {
            if (map == null || map.size() == 0) {
                return 0;
            }
            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>(map.size());
            for (String k : map.keySet()) {
                DefaultTypedTuple<String> tuple = new DefaultTypedTuple<>(k, map.get(k));
                tuples.add(tuple);
            }
            return zsetOps.add(key, tuples);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "zadd");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 删除指定键对应的有序集合中的一批元素。
     *
     * @param key    键。
     * @param values 要删除的字符串形式的元素。
     * @return 从集合中删除的元素数量。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long zrem(String key, Object... values) {
        try {
            return zsetOps.remove(key, values);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "zrem");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 获取指定键对应的有序集合中的元素个数。
     *
     * @param key 键。
     * @return 元素个数。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public long zcard(String key) {
        try {
            return zsetOps.zCard(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "zcard");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 从指定键对应的有序集合中获取给定下标范围的字符串形式的元素。
     * <p>元素顺序按分数由小到大。
     *
     * @param key   键。
     * @param start 起始下标。(下标从0开始)
     * @param end   结束下标。
     * @return 指定下标范围内的元素集合。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public Set<String> zrange(String key, long start, long end) {
        try {
            return zsetOps.range(key, start, end);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "zrange");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 从指定键对应的有序集合中获取给定下标范围的字符串形式的元素。
     * <p>元素顺序按分数由大到小。
     *
     * @param key   键。
     * @param start 起始下标。(下标从0开始)
     * @param end   结束下标。
     * @return 指定下标范围内的元素集合。
     * @throws RedisClientRuntimeException 如果发生异常等。
     */
    public Set<String> zrevrange(String key, long start, long end) {
        try {
            return zsetOps.reverseRange(key, start, end);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "zrevrange");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 向通道发布消息。
     *
     * @param channel 通道
     * @param message 消息。
     * @return
     *      接收到消息的订阅者数量。
     */
//    public long publish(final String channel, final String message){
//        return this.redisTemplate.execute(new RedisCallback<Long>() {
//            @Override
//            public Long doInRedis(RedisConnection connection) throws DataAccessException {
//                return connection.publish(channel.getBytes(Charsets.UTF_8), message.getBytes(Charsets.UTF_8));
//            }
//        });
//    }
//
//    /**
//     * 订阅目标通道的消息。
//     *
//     * @param messageListener 通道监听器
//     * @param channel 通道。
//     */
//    public void subscribe(SimpleMessageListener messageListener, String channel){
//        redisTemplate.execute(new RedisCallback<Void>() {
//            @Override
//            public Void doInRedis(RedisConnection connection) throws DataAccessException {
//                connection.subscribe(new MessageListener() {
//                    @Override
//                    public void onMessage(Message message, byte[] pattern) {
//                        messageListener.onMessage(new String(message.getBody(), Charsets.UTF_8));
//                    }
//                }, channel.getBytes(Charsets.UTF_8));
//                return null;
//            }
//        });
//    }

    /**
     * 订阅匹配目标模式通道的消息。
     *
     * @param listener 通道监听器
     * @param patterns 通道。
     */
    public void pSubscribe(final MessageListener listener, final byte[]... patterns) {
        redisTemplate.execute(new RedisCallback<Void>() {
            @Override
            public Void doInRedis(RedisConnection connection) throws DataAccessException {
                connection.pSubscribe(listener, patterns);
                return null;
            }
        });
    }

    /**
     * 添加指定元素到 HyperLogLog 中
     *
     * @param key    键
     * @param values 一个或者多个值
     * @return
     */
    public long pfAdd(String key, String... values) {
        try {
            return hyperLogLogOps.add(key, values);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "pfAdd");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 给定 HyperLogLog 的基数估算值
     * 如果多个 HyperLogLog 则返回基数估值之和
     *
     * @param keys
     * @return
     */
    public long pfCount(String... keys) {
        try {
            return hyperLogLogOps.size(keys);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "pfCount");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 将多个 HyperLogLog 合并为一个 HyperLogLog
     *
     * @param destKey   目标key
     * @param sourceKey 源key
     * @return 返回基数估算值
     */
    public long pfMerge(String destKey, String... sourceKey) {
        try {
            return hyperLogLogOps.union(destKey, sourceKey);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "pfMergepfMerge");
            throw new RedisClientRuntimeException(e);
        }
    }

    /**
     * 删除
     *
     * @param key
     */
    public void pfDel(String key) {
        try {
            hyperLogLogOps.delete(key);
        } catch (Throwable e) {
            redisExceptionMonitor(e, "pfDel");
            throw new RedisClientRuntimeException(e);
        }
    }

    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        try {
            return this.redisTemplate.execute(script, keys, args);
        } catch (Throwable var4) {
            this.redisExceptionMonitor(var4, "get");
            throw new RedisClientRuntimeException(var4);
        }
    }
}