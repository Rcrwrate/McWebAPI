# WebAPI 模组使用说明

## 功能概述
这个模组在Minecraft服务器启动时，会自动启动一个HTTP服务器，提供Web API接口用于与Minecraft服务器交互。

## 默认配置

- **HTTP端口**: 40002（可在配置文件中修改）
- **默认端点**:
  - `http://localhost:40002/` - 根路径，返回基本信息
  - `http://localhost:40002/status` - 状态检查，返回模组和服务器状态

## 配置文件

配置文件位置：`config/shirokasoke/WebAPI.cfg`

```ini
# Configuration file

general {
    # HTTP server port [range: 1024 ~ 65535, default: 40002]
    I:httpPort=40002

    # How shall I greet? [default: WebAPI loading]
    S:greeting=WebAPI loading
}
```

**配置文件路径说明**:
- 主配置目录: `config/`
- Mod专属目录: `shirokasoke/`
- 配置文件: `WebAPI.cfg`

完整路径: `config/shirokasoke/WebAPI.cfg`

## API端点

### 1. 根路径 `/`

**请求方法**: GET  
**返回格式**: JSON

**响应示例**:
```json
{
  "message": "Minecraft WebAPI is running",
  "modid": "webapi"
}
```

### 2. 状态检查 `/status`

**请求方法**: GET  
**返回格式**: JSON

**响应示例**:
```json
{
  "status": "online",
  "version": "1.0.0",
  "minecraft": "1.7.10"
}
```

### 3. TPS查询 `/tps`

**请求方法**: GET  
**返回格式**: JSON  
**说明**: 返回服务器的TPS（Ticks Per Second）和tick时间数据

**响应示例**:
```json
{
  "tps": {
    "1m": 20.00,
    "5m": 19.98,
    "15m": 19.95
  },
  "tickTimeMs": {
    "1m": 50.12,
    "5m": 50.35,
    "15m": 50.68
  },
  "meanTickTimeMs": 50.45
}
```

### 4. 方块信息查询 `/block`

**请求方法**: GET  
**返回格式**: JSON  
**说明**: 查询指定坐标和维度的方块信息

**查询参数**:
- `x` (必需): X坐标
- `y` (必需): Y坐标
- `z` (必需): Z坐标
- `dim` (可选): 维度ID，默认为0（主世界）
  - `0`: 主世界
  - `-1`: 下界
  - `1`: 末地

**请求示例**:
```bash
# 查询主世界坐标(100, 64, 200)的方块
curl "http://localhost:40002/block?x=100&y=64&z=200"

# 查询下界坐标(50, 32, -100)的方块
curl "http://localhost:40002/block?x=50&y=32&z=-100&dim=-1"
```

**响应示例**:
```json
{
  "coordinates": {
    "x": 100,
    "y": 64,
    "z": 200,
    "dimension": 0
  },
  "block": {
    "name": "minecraft:stone",
    "id": 1,
    "metadata": 0
  },
  "isAir": false
}
```

**错误响应示例**:
```json
// 缺少参数
{"error": "Missing query parameters. Required: x, y, z"}

// 维度不存在
{"error": "Invalid dimension: 5"}

// 区块未加载
{"error": "Chunk not loaded at coordinates: 100, 64, 200"}
```

**字段说明**:
- `coordinates`: 查询的坐标和维度信息
- `block`: 方块信息
  - `name`: 方块注册名称（如"minecraft:stone"）
  - `id`: 方块ID数字
  - `metadata`: 方块元数据（用于区分相同方块的不同状态）
- `isAir`: 是否为空气方块

**字段说明**:
- `tps`: TPS值（正常值为20.0，低于20表示卡顿）
  - `1m`: 过去1分钟的平均TPS
  - `5m`: 过去5分钟的平均TPS
  - `15m`: 过去15分钟的平均TPS
- `tickTimeMs`: Tick处理时间（毫秒）
  - `1m`: 过去1分钟的平均tick时间
  - `5m`: 过去5分钟的平均tick时间
  - `15m`: 过去15分钟的平均tick时间
- `meanTickTimeMs`: 总体平均tick时间

**TPS参考标准**:
- `20.0`: 完美，无卡顿
- `19.5+`: 良好，轻微卡顿
- `18.0+`: 可接受，中度卡顿
- `< 18.0`: 严重卡顿，需要优化

## 日志信息

服务器启动时，您会在控制台看到以下信息：

```
[WebAPI] WebAPI HTTP Server started on port 40002
[WebAPI] Available routes:
[WebAPI]   - / (GET): Returns basic information about the WebAPI
[WebAPI]   - /status (GET): Returns server status and version information
[WebAPI] Access at: http://localhost:40002/
```

## 测试API

您可以使用curl或浏览器测试API：

```bash
# 测试根路径
curl http://localhost:40002/

# 测试状态检查
curl http://localhost:40002/status

# 测试TPS查询
curl http://localhost:40002/tps

# 测试方块查询（查询主世界坐标100,64,200的方块）
curl "http://localhost:40002/block?x=100&y=64&z=200"

# 测试方块查询（查询下界坐标50,32,-100的方块）
curl "http://localhost:40002/block?x=50&y=32&z=-100&dim=-1"
```

或者直接在浏览器中访问上述URL。

### 使用jq美化JSON输出（推荐）

如果安装了jq工具，可以美化JSON输出：

```bash
# 查看TPS并格式化输出
curl -s http://localhost:40002/tps | jq .

# 只查看TPS值
curl -s http://localhost:40002/tps | jq '.tps'

# 查看1分钟平均TPS
curl -s http://localhost:40002/tps | jq '.tps["1m"]'

# 查看方块信息并格式化输出
curl -s "http://localhost:40002/block?x=100&y=64&z=200" | jq .

# 只查看方块名称
curl -s "http://localhost:40002/block?x=100&y=64&z=200" | jq '.block.name'

# 只查看是否为空气方块
curl -s "http://localhost:40002/block?x=100&y=64&z=200" | jq '.isAir'
```

## 路由系统

WebAPI使用模块化的路由系统，所有路由处理器都位于 `server/handlers/` 目录下。

详细的路由系统文档请参考 `ROUTING_SYSTEM.md`。

## 扩展API

### 快速添加新路由

1. **创建处理器类**（在 `server/handlers/` 目录下）：

```java
package love.shirokasoke.webapi.server.handlers;

import com.sun.net.httpserver.HttpExchange;
import love.shirokasoke.webapi.server.RouteHandler;

public class MyHandler implements RouteHandler {
    
    @Override
    public String getPath() {
        return "/myendpoint";
    }
    
    @Override
    public String getDescription() {
        return "My custom endpoint";
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String response = "{\"message\": \"Hello from my endpoint\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}
```

2. **注册路由**：

编辑 `server/RouteRegistry.java`，在 `initializeDefaultRoutes()` 方法中添加：

```java
public static void initializeDefaultRoutes() {
    register(new RootHandler());
    register(new StatusHandler());
    register(new MyHandler()); // 添加这一行
}
```

3. **重新启动服务器**，新路由将自动生效。

### 动态路由注册

您也可以在运行时动态添加路由：

```java
WebServer.addRoute(new MyHandler());
```

## 注意事项

- HTTP服务器会在Minecraft服务器启动时自动启动
- HTTP服务器会在Minecraft服务器停止时自动关闭
- 如果端口被占用，服务器会启动失败并在日志中显示错误
- 当前实现基于Java内置的HttpServer，无需额外依赖
- 路由系统支持模块化扩展，易于维护和添加新功能

## 下一步开发建议

您可以扩展以下功能：
- [x] 服务器TPS查询（/tps）✅
- [x] 方块信息查询（/block）✅
- [ ] 玩家在线状态查询（/players）
- [ ] 服务器性能数据（/metrics）
- [ ] 执行服务器命令的API（/command）
- [ ] 世界数据查询（/world）
- [ ] 物品查询（/items）
- [ ] 添加API认证机制
- [ ] 支持POST请求处理
- [ ] WebSocket支持实时数据推送

## 监控脚本示例

### Bash监控脚本

创建一个简单的监控脚本来持续监控服务器TPS：

```bash
#!/bin/bash
# tps-monitor.sh

API_URL="http://localhost:40002/tps"
CHECK_INTERVAL=30  # 每30秒检查一次
ALERT_TPS=18.0     # TPS低于此值发送警告

while true; do
    RESPONSE=$(curl -s $API_URL)
    TPS_1M=$(echo $RESPONSE | grep -oP '(?<="1m": )[0-9.]+')
    
    echo "[$(date)] TPS (1m): $TPS_1M"
    
    if (( $(echo "$TPS_1M < $ALERT_TPS" | bc -l) )); then
        echo "WARNING: TPS is low ($TPS_1M < $ALERT_TPS)"
        # 可以在这里添加通知逻辑，如发送邮件或Discord消息
    fi
    
    sleep $CHECK_INTERVAL
done
```

使用方式：
```bash
chmod +x tps-monitor.sh
./tps-monitor.sh
```

### Python监控脚本

```python
#!/usr/bin/env python3
import requests
import time
import json

API_URL = "http://localhost:40002/tps"
CHECK_INTERVAL = 30
ALERT_TPS = 18.0

def check_tps():
    try:
        response = requests.get(API_URL)
        data = response.json()
        
        tps_1m = data['tps']['1m']
        tick_time = data['tickTimeMs']['1m']
        
        print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] TPS: {tps_1m:.2f}, Tick Time: {tick_time:.2f}ms")
        
        if tps_1m < ALERT_TPS:
            print(f"⚠️  WARNING: Server TPS is low ({tps_1m:.2f} < {ALERT_TPS})")
            # 添加你的通知逻辑
            
    except Exception as e:
        print(f"Error checking TPS: {e}")

while True:
    check_tps()
    time.sleep(CHECK_INTERVAL)
```

## 性能分析

使用TPS数据可以帮助你：

1. **识别性能瓶颈**：观察TPS何时下降
2. **评估优化效果**：对比优化前后的TPS
3. **计划服务器重启**：当15分钟平均TPS持续低下时
4. **监控模组影响**：安装新模组后观察TPS变化

## Grafana集成（高级）

如果你想将TPS数据可视化，可以：

1. 使用Telegraf收集API数据
2. 存储到InfluxDB
3. 在Grafana中创建仪表盘

示例Telegraf配置：
```toml
[[inputs.httpjson]]
  name = "minecraft_tps"
  servers = ["http://localhost:40002/tps"]
  
[[outputs.influxdb]]
  urls = ["http://localhost:8086"]
  database = "minecraft"
```

## 相关文档

- `ROUTING_SYSTEM.md` - 详细的路由系统架构和高级用法
- `API_USAGE.md` - API使用指南（本文档）

## 技术支持

如果遇到问题，请检查：

1. 服务器是否已启动并运行
2. HTTP服务器端口是否被占用（默认40002）
3. 防火墙是否允许访问该端口
4. 查看服务器日志中的错误信息

