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

### 3. 批量设置（用于测试）
**接口**：`POST /api/redis/set-batch`

**参数**：
- `key`：Redis键（必填）
- `value`：值（必填）
- `count`：重复设置次数（可选，默认1）

**示例**：
```bash
curl -X POST "http://localhost:8080/api/redis/set-batch?key=hotkey:test&value=test&count=10"
```

### 4. 批量获取（用于测试热Key缓存）
**接口**：`GET /api/redis/get-batch`

**参数**：
- `key`：Redis键（必填）
- `count`：重复获取次数（可选，默认1）

**示例**：
```bash
# 连续获取10000次，用于触发热Key检测
curl "http://localhost:8080/api/redis/get-batch?key=hotkey:test&count=10000"
```

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

### 步骤3：观察日志
查看应用日志，应该能看到：
- 热Key晋升日志（每5秒）
- 热Key降级日志（每分钟）
- 监控统计信息（每分钟）

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
  enabled: true
  detection:
    hot-key-qps-threshold: 3000.0  # 热Key QPS阈值
    top-n: 10                       # Top N数量
    promotion-interval: 5000        # 晋升间隔（毫秒）
    demotion-interval: 60000       # 降级间隔（毫秒）
  storage:
    maximum-size: 100               # 最大缓存数量
    expire-after-write: 60         # 过期时间（秒）
```

## 注意事项

1. **Redis必须运行**：确保Redis服务在 `127.0.0.1:6379` 运行
2. **端口冲突**：如果8080端口被占用，修改 `application.yml` 中的 `server.port`
3. **热Key阈值**：默认QPS阈值是3000，需要高频访问才能触发，测试时可以降低阈值
4. **日志级别**：默认热Key相关日志是DEBUG级别，可以在配置文件中调整

## 故障排查

### 1. 无法连接Redis
- 检查Redis是否运行：`redis-cli ping`
- 检查Redis地址和端口配置

### 2. 端口被占用
- 修改 `application.yml` 中的 `server.port`
- 或停止占用8080端口的进程

### 3. 热Key未触发
- 检查访问频率是否达到阈值（默认3000 QPS）
- 降低 `hot-key-qps-threshold` 进行测试
- 查看日志确认访问是否被记录
