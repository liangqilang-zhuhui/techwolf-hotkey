# Demo使用指南

## 启动说明

### 1. 前置条件
- JDK 1.8+
- Maven 3.6+
- Redis 服务器运行在 `127.0.0.1:6379`

### 2. 启动Redis
```bash
# 确保Redis服务已启动
redis-server
# 或使用Docker
docker run -d -p 6379:6379 redis:latest
```

### 3. 启动应用
```bash
# 方式1：使用Maven运行
mvn spring-boot:run

# 方式2：打包后运行
mvn clean package
java -jar target/hotkey-1.0.0.jar
```

### 4. 验证启动
应用启动后，访问：http://localhost:8080

## API接口说明

### 1. 设置值
**接口**：`POST /api/redis/set`

**参数**：
- `key`：Redis键（必填）
- `value`：值（必填）

**示例**：
```bash
curl -X POST "http://localhost:8080/api/redis/set?key=user:123&value=test123"
```

**响应**：
```
设置成功: key=user:123
```

### 2. 获取值
**接口**：`GET /api/redis/get`

**参数**：
- `key`：Redis键（必填）

**示例**：
```bash
curl "http://localhost:8080/api/redis/get?key=user:123"
```

**响应**：
```
test123
```

### 3. 删除值
**接口**：`POST /api/redis/del`

**参数**：
- `key`：Redis键（必填）

**示例**：
```bash
curl -X POST "http://localhost:8080/api/redis/del?key=user:123"
```

**响应**：
```
删除成功: key=user:123
```

### 4. 批量设置（用于测试）
**接口**：`POST /api/redis/set-batch`

**参数**：
- `key`：Redis键（必填）
- `value`：值（必填）
- `count`：重复设置次数（可选，默认1）

**示例**：
```bash
curl -X POST "http://localhost:8080/api/redis/set-batch?key=hotkey:test&value=test&count=10"
```

### 5. 批量获取（用于测试热Key缓存）
**接口**：`GET /api/redis/get-batch`

**参数**：
- `key`：Redis键（必填）
- `count`：重复获取次数（可选，默认1）

**示例**：
```bash
# 连续获取10000次，用于触发热Key检测
curl "http://localhost:8080/api/redis/get-batch?key=hotkey:test&count=10000"
```

## 监控API接口说明

### 1. 获取完整监控信息
**接口**：`GET /api/hotkey/monitor/info`

**示例**：
```bash
curl "http://localhost:8080/api/hotkey/monitor/info"
```

**响应**：返回JSON格式的完整监控信息，包括热Key列表、统计信息等

### 2. 检查指定key是否为热Key
**接口**：`GET /api/hotkey/monitor/check`

**参数**：
- `key`：Redis键（必填）

**示例**：
```bash
curl "http://localhost:8080/api/hotkey/monitor/check?key=hotkey:test"
```

**响应**：返回JSON格式，包含是否为热Key、热Key总数等信息

### 3. 获取热Key列表
**接口**：`GET /api/hotkey/monitor/hotkeys`

**示例**：
```bash
curl "http://localhost:8080/api/hotkey/monitor/hotkeys"
```

**响应**：返回JSON格式，包含当前所有热Key列表

### 4. 获取统计信息
**接口**：`GET /api/hotkey/monitor/stats`

**示例**：
```bash
curl "http://localhost:8080/api/hotkey/monitor/stats"
```

**响应**：返回JSON格式，包含QPS、命中率、流量占比等统计信息

### 5. 手动刷新监控数据
**接口**：`POST /api/hotkey/monitor/refresh`

**示例**：
```bash
curl -X POST "http://localhost:8080/api/hotkey/monitor/refresh"
```

**响应**：返回JSON格式，包含操作结果

## 测试热Key功能

### 步骤1：设置一个key
```bash
curl -X POST "http://localhost:8080/api/redis/set?key=hotkey:test&value=test_value"
```

### 步骤2：高频访问该key（触发热Key检测）
```bash
# 使用脚本快速访问
for i in {1..10000}; do
  curl -s "http://localhost:8080/api/redis/get?key=hotkey:test" > /dev/null
done
```

### 步骤3：观察日志和监控
查看应用日志，应该能看到：
- 热Key晋升日志（每5秒）
- 热Key降级日志（每20次晋升执行1次，即约每100秒）
- 监控统计信息（每分钟）

也可以通过监控API实时查看：
```bash
# 查看热Key列表
curl "http://localhost:8080/api/hotkey/monitor/hotkeys"

# 查看统计信息
curl "http://localhost:8080/api/hotkey/monitor/stats"

# 检查指定key是否为热Key
curl "http://localhost:8080/api/hotkey/monitor/check?key=hotkey:test"
```

### 步骤4：验证缓存效果
当key成为热Key后，后续的get操作会从本地缓存获取，响应速度会明显提升。

## 配置说明

配置文件：`src/main/resources/application.yml`

### 服务器配置
```yaml
server:
  port: 8080  # 服务端口
```

### Redis配置
```yaml
spring:
  redis:
    host: 127.0.0.1  # Redis地址
    port: 6379        # Redis端口
```

### 热Key配置
```yaml
hotkey:
  enabled: true                          # 是否启用，默认true
  detection:
    top-n: 20                            # Top N数量，默认20
    hot-key-qps-threshold: 20            # 热Key QPS阈值（次/秒），默认500（测试时降低到20）
    warm-key-qps-threshold: 10           # 温Key QPS阈值（次/秒），默认200（测试时降低到10）
    promotion-interval: 5000             # 晋升间隔（毫秒），默认5000（5秒）
  storage:
    maximum-size: 2000                   # 最大缓存数量，默认2000（TOP_N * 100）
    expire-after-write: 60               # 过期时间（分钟），默认60分钟
  recorder:
    max-capacity: 100000                 # 访问记录最大容量，默认100000
    window-size: 10                      # 统计窗口大小（秒），默认10秒
    inactive-expire-time: 120            # 非活跃key过期时间（秒），默认120秒
  refresh:
    interval: 10000                      # 刷新间隔（毫秒），默认10000（10秒）
  monitor:
    interval: 60000                      # 监控输出间隔（毫秒），默认60000（60秒）
    prometheus:
      enabled: true                      # 启用Prometheus指标，默认true
```

**配置参数说明**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | boolean | true | 是否启用热Key检测 |
| `detection.top-n` | int | 20 | Top N数量，最多保留的热Key数量 |
| `detection.hot-key-qps-threshold` | int | 500 | 热Key QPS阈值（次/秒），只有QPS >= 500的key才能成为热Key |
| `detection.warm-key-qps-threshold` | int | 200 | 温Key QPS阈值（次/秒），冷key需要QPS >= 200才能升级到温key |
| `detection.promotion-interval` | long | 5000 | 晋升间隔（毫秒），每5秒执行一次热Key晋升检测 |
| `storage.maximum-size` | long | 2000 | 最大缓存数量（TOP_N * 100） |
| `storage.expire-after-write` | int | 60 | 写入后过期时间（分钟），默认60分钟 |
| `recorder.max-capacity` | int | 100000 | 访问记录最大容量，超限时自动清理低QPS的key |
| `recorder.window-size` | int | 10 | 统计窗口大小（秒），用于计算QPS |
| `recorder.inactive-expire-time` | int | 120 | 非活跃key过期时间（秒），超过此时间未访问的key会被清理 |
| `refresh.interval` | long | 10000 | 刷新间隔（毫秒），默认10秒刷新一次热Key数据 |
| `monitor.enabled` | boolean | true | 是否启用监控 |
| `monitor.interval` | long | 60000 | 监控输出间隔（毫秒），默认60秒输出一次 |
| `monitor.prometheus.enabled` | boolean | true | 是否启用Prometheus指标 |

**重要说明**：
- `hot-key-qps-threshold` 和 `warm-key-qps-threshold` 参数类型为 **int**，配置时使用整数即可
- 测试时可以降低 `hot-key-qps-threshold` 和 `warm-key-qps-threshold` 以便快速验证功能（如设置为20和10）
- `top-n`：同时满足QPS阈值和Top N排名的key才会晋升为热Key
- `promotion-interval`：晋升任务执行间隔，每5秒检查一次
- 降级任务由代码控制，每执行20次晋升任务执行1次降级任务
- `expire-after-write`：缓存过期时间，单位是**分钟**（不是秒）
- `refresh.interval`：数据刷新间隔，应小于`expire-after-write`，确保在过期前刷新

## 注意事项

1. **Redis必须运行**：确保Redis服务在 `127.0.0.1:6379` 运行
2. **端口冲突**：如果8080端口被占用，修改 `application.yml` 中的 `server.port`
3. **热Key阈值**：
   - 默认热Key QPS阈值是500，温Key QPS阈值是200，但demo中配置为20和10以便测试
   - 需要同时满足QPS阈值和Top N排名才能成为热Key
   - 测试时可以降低`hot-key-qps-threshold`和`warm-key-qps-threshold`到较小值（如20和10）
   - 注意：这些参数类型为 **int**，配置时使用整数即可
4. **日志级别**：默认热Key相关日志是DEBUG级别，可以在配置文件中调整
5. **缓存过期时间**：`expire-after-write`的单位是**分钟**，不是秒
6. **Prometheus监控**：如果启用了Prometheus，可以通过 `/actuator/prometheus` 端点查看指标

## 故障排查

### 1. 无法连接Redis
- 检查Redis是否运行：`redis-cli ping`
- 检查Redis地址和端口配置

### 2. 端口被占用
- 修改 `application.yml` 中的 `server.port`
- 或停止占用8080端口的进程

### 3. 热Key未触发
- 检查访问频率是否达到阈值（demo中配置为20 QPS，默认500 QPS）
- 需要同时满足QPS阈值和Top N排名
- 降低 `hot-key-qps-threshold` 和 `warm-key-qps-threshold` 进行测试（建议设置为20和10）
- 注意：这些参数类型为 **int**，配置时使用整数，不要使用小数（如使用 `20` 而不是 `20.0`）
- 查看日志确认访问是否被记录
- 使用监控API检查：`curl "http://localhost:8080/api/hotkey/monitor/stats"`

### 4. Prometheus指标查看
- 访问：`http://localhost:8080/actuator/prometheus`
- 查看热Key相关的指标，如 `hotkey_*` 开头的指标