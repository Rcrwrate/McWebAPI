# WebAPI 项目结构

## 完整目录结构

```
/workspace/
├── src/main/java/love/shirokasoke/webapi/
│   ├── MyMod.java                          # 主模组类
│   ├── CommonProxy.java                    # 服务端代理
│   ├── ClientProxy.java                    # 客户端代理
│   ├── Config.java                         # 配置管理
│   └── server/                             # Web服务器模块
│       ├── WebServer.java                  # HTTP服务器管理器
│       ├── RouteHandler.java               # 路由接口
│       ├── RouteRegistry.java              # 路由注册器
│       └── handlers/                       # 路由处理器
│           ├── RootHandler.java            # 根路径 / 
│           ├── StatusHandler.java          # 状态 /status
│           ├── TPSHandler.java             # TPS查询 /tps ⭐
│           └── NotFoundHandler.java        # 404处理器
├── src/main/resources/
│   └── mcmod.info                          # 模组信息
├── build.gradle.kts                        # Gradle构建脚本
├── gradle.properties                       # 配置文件（含modid和端口）
├── API_USAGE.md                            # API使用指南
├── ROUTING_SYSTEM.md                       # 路由系统文档
└── PROJECT_STRUCTURE.md                    # 项目结构（本文档）
```

## 文件说明

### 核心模组文件

| 文件 | 行数 | 说明 |
|------|------|------|
| `MyMod.java` | 53 | 主模组类，管理Forge生命周期事件 |
| `CommonProxy.java` | 38 | 服务端代理，处理服务器启动/停止 |
| `ClientProxy.java` | 8 | 客户端代理（当前为空实现） |
| `Config.java` | 23 | 配置管理，支持端口和欢迎消息配置 |

### Web服务器模块

| 文件 | 行数 | 说明 |
|------|------|------|
| `server/WebServer.java` | 116 | HTTP服务器管理器，负责启动/停止 |
| `server/RouteHandler.java` | 31 | 路由处理器接口 |
| `server/RouteRegistry.java` | 48 | 路由注册管理器，管理所有路由 |
| `server/handlers/RootHandler.java` | 34 | 根路径处理器 |
| `server/handlers/StatusHandler.java` | 35 | 状态检查处理器 |
| `server/handlers/TPSHandler.java` | 152 | ⭐ TPS查询处理器（新增） |
| `server/handlers/NotFoundHandler.java` | 34 | 404错误处理器 |

## API路由列表

| 路径 | 方法 | 说明 | 处理器 |
|------|------|------|--------|
| `/` | GET | 返回基本信息 | RootHandler |
| `/status` | GET | 返回服务器状态 | StatusHandler |
| `/tps` | GET | ⭐ 返回TPS性能数据 | TPSHandler |
| `/notfound` | GET | 404错误页面 | NotFoundHandler |

## 配置项

### gradle.properties
```properties
modId = webapi
modGroup = love.shirokasoke.webapi
httpPort = 40002
```

### 运行时配置 (config/shirokasoke/WebAPI.cfg)
```ini
general {
    I:httpPort=40002          # HTTP端口
    S:greeting=WebAPI loading # 欢迎消息
}
```

**配置文件路径**: `config/shirokasoke/WebAPI.cfg`

## 依赖关系

```
MyMod
├── CommonProxy
│   ├── Config (配置)
│   └── WebServer (HTTP服务器)
│       ├── RouteRegistry (路由注册)
│       │   └── handlers/* (路由处理器)
│       └── HttpServer (Java内置)
└── Tags (版本信息)
```

## 代码统计

- **总文件数**: 12个Java文件
- **总代码行数**: ~650行
- **核心功能**: HTTP API服务器
- **测试覆盖**: 3个API端点

## 扩展点

要添加新功能，可以：

1. **添加新路由**: 在 `server/handlers/` 创建新处理器
2. **注册路由**: 在 `RouteRegistry.initializeDefaultRoutes()` 中注册
3. **添加配置**: 在 `Config.java` 中添加新配置项
4. **添加代理方法**: 在 `CommonProxy.java` 中添加事件处理

## 构建和运行

### 构建模组
```bash
./gradlew build
```

### 运行开发环境
```bash
./gradlew runServer
```

### 测试API
```bash
curl http://localhost:40002/tps | jq .
```

## 版本信息

- **Minecraft**: 1.7.10
- **Forge**: 10.13.4.1614
- **Java**: 8+
- **项目结构**: 专业级模块化设计
