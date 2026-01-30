package cn.techwolf.datastar.hotkey.demo;

import cn.techwolf.datastar.hotkey.IHotKeyClient;
import cn.techwolf.datastar.hotkey.monitor.IHotKeyMonitor;
import cn.techwolf.datastar.hotkey.monitor.MonitorInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 热Key监控Controller
 * 提供监控API，用于测试脚本查询热Key状态和监控指标
 *
 * @author techwolf
 * @date 2024
 */
@Slf4j
@RestController
@RequestMapping("/api/hotkey/monitor")
public class HotKeyMonitorController {

    @Autowired(required = false)
    private IHotKeyClient hotKeyClient;

    /**
     * 获取完整监控信息
     *
     * @return 监控信息（JSON格式）
     */
    @GetMapping("/info")
    public Map<String, Object> getMonitorInfo() {
        Map<String, Object> result = new HashMap<>();
        
        if (hotKeyClient == null || !hotKeyClient.isEnabled()) {
            result.put("enabled", false);
            result.put("message", "热Key客户端未启用");
            return result;
        }

        IHotKeyMonitor monitor = hotKeyClient.getHotKeyMonitor();
        if (monitor == null) {
            result.put("enabled", false);
            result.put("message", "热Key监控器未初始化");
            return result;
        }

        try {
            MonitorInfo monitorInfo = monitor.getMonitorInfo();
            
            result.put("enabled", true);
            result.put("hotKeyCount", monitorInfo.getHotKeyCount());
            result.put("hotKeys", monitorInfo.getHotKeys());
            result.put("storageSize", monitorInfo.getStorageSize());
            result.put("recorderSize", monitorInfo.getRecorderSize());
            result.put("recorderMemorySize", monitorInfo.getRecorderMemorySize());
            result.put("totalWrapGetCount", monitorInfo.getTotalWrapGetCount());
            result.put("wrapGetQps", monitorInfo.getWrapGetQps());
            result.put("keysPerSecond", monitorInfo.getKeysPerSecond());
            result.put("hotKeyAccessCount", monitorInfo.getHotKeyAccessCount());
            result.put("hotKeyAccessQps", monitorInfo.getHotKeyAccessQps());
            result.put("hotKeyHitCount", monitorInfo.getHotKeyHitCount());
            result.put("hotKeyHitQps", monitorInfo.getHotKeyHitQps());
            result.put("hotKeyMissCount", monitorInfo.getHotKeyMissCount());
            result.put("hotKeyMissQps", monitorInfo.getHotKeyMissQps());
            result.put("hotKeyHitRate", monitorInfo.getHotKeyHitRate());
            result.put("hotKeyTrafficRatio", monitorInfo.getHotKeyTrafficRatio());
            
            return result;
        } catch (Exception e) {
            log.error("获取监控信息失败", e);
            result.put("enabled", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 检查指定key是否为热Key
     *
     * @param key Redis key
     * @return 检查结果
     */
    @GetMapping("/check")
    public Map<String, Object> checkHotKey(@RequestParam String key) {
        Map<String, Object> result = new HashMap<>();
        
        if (hotKeyClient == null || !hotKeyClient.isEnabled()) {
            result.put("enabled", false);
            result.put("isHotKey", false);
            result.put("message", "热Key客户端未启用");
            return result;
        }

        IHotKeyMonitor monitor = hotKeyClient.getHotKeyMonitor();
        if (monitor == null) {
            result.put("enabled", false);
            result.put("isHotKey", false);
            result.put("message", "热Key监控器未初始化");
            return result;
        }

        try {
            MonitorInfo monitorInfo = monitor.getMonitorInfo();
            Set<String> hotKeys = monitorInfo.getHotKeys();
            boolean isHotKey = hotKeys != null && hotKeys.contains(key);
            
            result.put("enabled", true);
            result.put("key", key);
            result.put("isHotKey", isHotKey);
            result.put("hotKeyCount", monitorInfo.getHotKeyCount());
            
            return result;
        } catch (Exception e) {
            log.error("检查热Key失败, key: {}", key, e);
            result.put("enabled", false);
            result.put("isHotKey", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 获取热Key列表
     *
     * @return 热Key列表
     */
    @GetMapping("/hotkeys")
    public Map<String, Object> getHotKeys() {
        Map<String, Object> result = new HashMap<>();
        
        if (hotKeyClient == null || !hotKeyClient.isEnabled()) {
            result.put("enabled", false);
            result.put("hotKeys", null);
            result.put("hotKeyCount", 0);
            result.put("message", "热Key客户端未启用");
            return result;
        }

        IHotKeyMonitor monitor = hotKeyClient.getHotKeyMonitor();
        if (monitor == null) {
            result.put("enabled", false);
            result.put("hotKeys", null);
            result.put("hotKeyCount", 0);
            result.put("message", "热Key监控器未初始化");
            return result;
        }

        try {
            MonitorInfo monitorInfo = monitor.getMonitorInfo();
            Set<String> hotKeys = monitorInfo.getHotKeys();
            
            result.put("enabled", true);
            result.put("hotKeys", hotKeys);
            result.put("hotKeyCount", hotKeys != null ? hotKeys.size() : 0);
            
            return result;
        } catch (Exception e) {
            log.error("获取热Key列表失败", e);
            result.put("enabled", false);
            result.put("hotKeys", null);
            result.put("hotKeyCount", 0);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> result = new HashMap<>();
        
        if (hotKeyClient == null || !hotKeyClient.isEnabled()) {
            result.put("enabled", false);
            result.put("message", "热Key客户端未启用");
            return result;
        }

        IHotKeyMonitor monitor = hotKeyClient.getHotKeyMonitor();
        if (monitor == null) {
            result.put("enabled", false);
            result.put("message", "热Key监控器未初始化");
            return result;
        }

        try {
            MonitorInfo monitorInfo = monitor.getMonitorInfo();
            
            result.put("enabled", true);
            result.put("totalWrapGetCount", monitorInfo.getTotalWrapGetCount());
            result.put("wrapGetQps", monitorInfo.getWrapGetQps());
            result.put("keysPerSecond", monitorInfo.getKeysPerSecond());
            result.put("hotKeyAccessCount", monitorInfo.getHotKeyAccessCount());
            result.put("hotKeyAccessQps", monitorInfo.getHotKeyAccessQps());
            result.put("hotKeyHitCount", monitorInfo.getHotKeyHitCount());
            result.put("hotKeyHitQps", monitorInfo.getHotKeyHitQps());
            result.put("hotKeyMissCount", monitorInfo.getHotKeyMissCount());
            result.put("hotKeyMissQps", monitorInfo.getHotKeyMissQps());
            result.put("hotKeyHitRate", monitorInfo.getHotKeyHitRate());
            result.put("hotKeyHitRatePercent", monitorInfo.getHotKeyHitRate() * 100);
            result.put("hotKeyTrafficRatio", monitorInfo.getHotKeyTrafficRatio());
            result.put("hotKeyTrafficRatioPercent", monitorInfo.getHotKeyTrafficRatio() * 100);
            
            return result;
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            result.put("enabled", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 手动刷新监控数据
     *
     * @return 操作结果
     */
    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        Map<String, Object> result = new HashMap<>();
        
        if (hotKeyClient == null || !hotKeyClient.isEnabled()) {
            result.put("success", false);
            result.put("message", "热Key客户端未启用");
            return result;
        }

        IHotKeyMonitor monitor = hotKeyClient.getHotKeyMonitor();
        if (monitor == null) {
            result.put("success", false);
            result.put("message", "热Key监控器未初始化");
            return result;
        }

        try {
            // 触发监控数据采集
            if (monitor instanceof cn.techwolf.datastar.hotkey.monitor.HotKeyMonitor) {
                ((cn.techwolf.datastar.hotkey.monitor.HotKeyMonitor) monitor).monitor();
            }
            
            result.put("success", true);
            result.put("message", "监控数据刷新成功");
            return result;
        } catch (Exception e) {
            log.error("刷新监控数据失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
}
