# 热Key功能测试说明

## 测试脚本说明

本目录包含热Key功能的完整测试脚本，用于验证热Key检测功能的完整性、合理性和潜在bug。

## 测试配置

根据当前配置：
- **热Key QPS阈值**: 30.0 (QPS >= 30 才能成为热Key)
- **温Key QPS阈值**: 10.0 (QPS >= 10 才能成为温Key)
- **访问记录最大容量**: 15 (最多记录15个key的访问统计)

## 测试脚本

### 1. Python测试脚本 (推荐)

**文件**: `hotkey_test.py`

**使用方法**:
```bash
# 确保已安装Python 3和requests库
pip install requests

# 运行测试
python3 hotkey_test.py
```

**测试内容**:
1. **基础功能测试**: 验证基本的set/get操作
2. **热Key检测测试**: 高频访问触发热Key（QPS > 30）
3. **温Key检测测试**: 中等频率访问触发温Key（10 <= QPS < 30）
4. **容量限制测试**: 测试recorderMaxCapacity=15的限制
5. **并发访问测试**: 多线程并发访问测试
6. **缓存命中率测试**: 验证热Key缓存命中率
7. **边界情况测试**: 空值、特殊字符、Unicode、长值等
8. **不存在的key测试**: 访问不存在的key
9. **多个热Key测试**: 同时存在多个热Key
10. **值更新测试**: 热Key值更新后缓存自动刷新

### 2. Shell测试脚本

**文件**: `hotkey_test.sh`

**使用方法**:
```bash
chmod +x hotkey_test.sh
./hotkey_test.sh
```

**测试内容**:
- 基础功能测试
- 热Key触发测试
- 批量访问测试

## 测试前准备

1. **启动Redis服务**
   ```bash
   redis-server
   # 或使用Docker
   docker run -d -p 6379:6379 redis:latest
   ```

2. **启动应用**
   ```bash
   mvn spring-boot:run
   ```

3. **验证服务可用**
   ```bash
   curl http://localhost:8080/api/redis/get?key=test
   ```

## 监控API说明

测试脚本使用以下监控API来验证热Key状态和获取监控指标：

### 1. 检查指定key是否为热Key
**接口**：`GET /api/hotkey/monitor/check?key={key}`

**示例**：
```bash
curl "http://localhost:8080/api/hotkey/monitor/check?key=test:hotkey:1"
```

**响应**：
```json
{
  "enabled": true,
  "key": "test:hotkey:1",
  "isHotKey": true,
  "hotKeyCount": 2
}
```

### 2. 获取完整监控信息
**接口**：`GET /api/hotkey/monitor/info`

**示例**：
```bash
curl "http://localhost:8080/api/hotkey/monitor/info"
```

**响应**：包含所有监控指标，如热Key数量、命中率、QPS等

### 3. 获取热Key列表
**接口**：`GET /api/hotkey/monitor/hotkeys`

**示例**：
```bash
curl "http://localhost:8080/api/hotkey/monitor/hotkeys"
```

### 4. 获取统计信息
**接口**：`GET /api/hotkey/monitor/stats`

**示例**：
```bash
curl "http://localhost:8080/api/hotkey/monitor/stats"
```

### 5. 手动刷新监控数据
**接口**：`POST /api/hotkey/monitor/refresh`

**示例**：
```bash
curl -X POST "http://localhost:8080/api/hotkey/monitor/refresh"
```

## 测试场景说明

### 场景1: 热Key检测
- **目标**: 验证高频访问（QPS > 30）能正确触发热Key检测
- **方法**: 在短时间内对同一个key进行大量访问
- **验证点**: 
  - 访问统计是否正确
  - 热Key是否被正确识别
  - 缓存是否生效

### 场景2: 温Key检测
- **目标**: 验证中等频率访问（10 <= QPS < 30）能正确触发温Key检测
- **方法**: 控制访问频率在20 QPS左右
- **验证点**: 
  - 温Key是否被正确识别
  - 不会误判为热Key

### 场景3: 容量限制
- **目标**: 验证当访问记录超过最大容量时，系统能正确处理
- **方法**: 创建超过15个不同的key并进行访问
- **验证点**: 
  - 容量限制是否生效
  - 低QPS的key是否被正确清理
  - 高QPS的key是否被保留

### 场景4: 并发访问
- **目标**: 验证多线程并发访问时的正确性
- **方法**: 使用10个线程同时访问同一个key
- **验证点**: 
  - 无数据竞争
  - 返回值正确
  - 无异常抛出

### 场景5: 缓存命中率
- **目标**: 验证热Key缓存的命中率
- **方法**: 触发热Key后连续访问
- **验证点**: 
  - 缓存命中率应该 >= 90%
  - 返回值正确

### 场景6: 边界情况
- **目标**: 验证各种边界情况的处理
- **方法**: 测试空值、特殊字符、Unicode、长值等
- **验证点**: 
  - 所有边界情况都能正确处理
  - 无异常抛出

### 场景7: 值更新
- **目标**: 验证热Key值更新后缓存自动刷新
- **方法**: 触发热Key后更新Redis中的值，等待自动刷新
- **验证点**: 
  - 缓存能在刷新间隔内自动更新
  - 更新后的值正确

## 预期结果

所有测试应该通过，表明：
1. ✅ 热Key检测功能正常工作
2. ✅ 缓存机制正确
3. ✅ 容量限制有效
4. ✅ 并发访问安全
5. ✅ 边界情况处理正确
6. ✅ 自动刷新机制正常

## 常见问题

### Q: 测试失败，提示连接超时
**A**: 确保应用已启动并运行在8080端口，检查防火墙设置

### Q: 热Key检测测试失败
**A**: 检查配置的QPS阈值，确保访问频率足够高。可以增加访问次数或减少等待时间

### Q: 缓存命中率低于预期
**A**: 确保等待足够的时间让热Key晋升完成（至少6秒），然后再测试缓存命中率

### Q: 容量限制测试失败
**A**: 容量限制可能导致部分低QPS的key被清理，这是正常行为。测试主要验证系统不会崩溃

## 性能指标

测试完成后，可以通过以下方式查看性能指标：

### 1. 通过监控API
使用监控API实时查询：
```bash
# 获取完整监控信息
curl "http://localhost:8080/api/hotkey/monitor/info"

# 获取统计信息
curl "http://localhost:8080/api/hotkey/monitor/stats"
```

### 2. 通过应用日志
查看应用日志中的监控输出，包含以下指标：
- 热Key数量
- 缓存命中率
- 访问记录模块数据量
- wrapGet总调用次数
- wrapGet的QPS

## 扩展测试

可以根据需要添加以下测试：
- 压力测试：长时间高并发访问
- 内存泄漏测试：长时间运行后检查内存使用
- 降级测试：热Key降级为温Key或冷Key
- 故障恢复测试：Redis连接断开后的恢复
