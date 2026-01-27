package cn.techwolf.datastar.hotkey.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Redis操作Controller
 * 提供set和get接口，演示热Key缓存功能
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
@RestController
@RequestMapping("/api/redis")
public class RedisController {

    @Autowired
    private RedisClientManager redisClientManager;

    /**
     * 设置值
     *
     * @param key   键
     * @param value 值
     * @return 操作结果
     */
    @PostMapping("/set")
    public String set(@RequestParam String key, @RequestParam String value) {
        try {
            redisClientManager.set(key, value);
            log.info("设置成功: key={}, value={}", key, value);
            return "设置成功: key=" + key;
        } catch (Exception e) {
            log.error("设置失败: key={}, value={}", key, value, e);
            return "设置失败: " + e.getMessage();
        }
    }

    /**
     * 获取值
     *
     * @param key 键
     * @return 值
     */
    @GetMapping("/get")
    public String get(@RequestParam String key) {
        try {
            String value = redisClientManager.get(key);
            log.info("获取成功: key={}, value={}", key, value);
            return value != null ? value : "key不存在";
        } catch (Exception e) {
            log.error("获取失败: key={}", key, e);
            return "获取失败: " + e.getMessage();
        }
    }

    /**
     * 批量设置值（用于测试热Key）
     *
     * @param key   键
     * @param value 值
     * @param count 重复设置次数（用于触发热Key）
     * @return 操作结果
     */
    @PostMapping("/set-batch")
    public String setBatch(@RequestParam String key, 
                          @RequestParam String value,
                          @RequestParam(defaultValue = "1") int count) {
        try {
            for (int i = 0; i < count; i++) {
                redisClientManager.set(key, value + "_" + i);
            }
            log.info("批量设置成功: key={}, count={}", key, count);
            return "批量设置成功: key=" + key + ", count=" + count;
        } catch (Exception e) {
            log.error("批量设置失败: key={}, count={}", key, count, e);
            return "批量设置失败: " + e.getMessage();
        }
    }

    /**
     * 批量获取值（用于测试热Key缓存）
     *
     * @param key   键
     * @param count 重复获取次数（用于触发热Key）
     * @return 最后一次获取的值
     */
    @GetMapping("/get-batch")
    public String getBatch(@RequestParam String key,
                          @RequestParam(defaultValue = "1") int count) {
        try {
            String value = null;
            for (int i = 0; i < count; i++) {
                value = redisClientManager.get(key);
            }
            log.info("批量获取成功: key={}, count={}, value={}", key, count, value);
            return value != null ? value : "key不存在";
        } catch (Exception e) {
            log.error("批量获取失败: key={}, count={}", key, count, e);
            return "批量获取失败: " + e.getMessage();
        }
    }

    /**
     * 删除值
     *
     * @param key 键
     * @return 操作结果
     */
    @PostMapping("/del")
    public String del(@RequestParam String key) {
        try {
            boolean deleted = redisClientManager.del(key);
            if (deleted) {
                log.info("删除成功: key={}", key);
                return "删除成功: key=" + key;
            } else {
                log.info("key不存在: key={}", key);
                return "key不存在: key=" + key;
            }
        } catch (Exception e) {
            log.error("删除失败: key={}", key, e);
            return "删除失败: " + e.getMessage();
        }
    }
}
