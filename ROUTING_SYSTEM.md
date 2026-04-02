# WebAPI 路由系统使用指南

## 目录结构

```
love/shirokasoke/webapi/
├── MyMod.java              # 主模组类
├── CommonProxy.java        # 代理类
├── Config.java            # 配置管理
├── ClientProxy.java       # 客户端代理
└── server/                # Web服务器模块
    ├── WebServer.java     # HTTP服务器管理
    ├── RouteHandler.java  # 路由处理器接口
    ├── RouteRegistry.java # 路由注册管理器
    └── handlers/          # 路由处理器实现
        ├── RootHandler.java     # 根路径处理器
        ├── StatusHandler.java   # 状态检查处理器
        └── NotFoundHandler.java # 404处理器
```

## 路由系统架构

### 1. RouteHandler 接口

所有路由处理器都必须实现此接口：

```java
public interface RouteHandler extends HttpHandler {
    String getPath();              // 返回路由路径（如 "/status"）
    String getMethod();            // 返回HTTP方法（默认为GET）
    String getDescription();       // 返回路由描述
    void handle(HttpExchange exchange); // 处理请求
}
```

### 2. RouteRegistry 路由注册器

管理所有路由的注册和获取：

```java
// 注册路由
RouteRegistry.register(new MyHandler());

// 获取所有路由
Map<String, RouteHandler> routes = RouteRegistry.getAllRoutes();

// 初始化默认路由
RouteRegistry.initializeDefaultRoutes();
```

### 3. WebServer HTTP服务器

管理服务器生命周期和路由注册：

```java
// 启动服务器
WebServer.start(port);

// 停止服务器
WebServer.stop();

// 动态添加路由
WebServer.addRoute(new MyHandler());
```

## 创建新的路由处理器

### 步骤1：创建处理器类

在 `server/handlers/` 目录下创建新的处理器类：

```java
package love.shirokasoke.webapi.server.handlers;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;

public class MyHandler implements RouteHandler {
    
    @Override
    public String getPath() {
        return "/myendpoint";
    }
    
    @Override
    public String getMethod() {
        return "GET";
    }
    
    @Override
    public String getDescription() {
        return "My custom endpoint description";
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

### 步骤2：注册路由

在 `RouteRegistry.java` 中注册新路由：

```java
public static void initializeDefaultRoutes() {
    // 注册根路由
    register(new RootHandler());
    
    // 注册状态路由
    register(new StatusHandler());
    
    // 注册你的新路由
    register(new MyHandler());
}
```

### 步骤3：重新启动服务器

重新启动Minecraft服务器，新路由将自动注册。

## 动态路由注册

你也可以在运行时动态添加路由：

```java
// 在任何地方调用
WebServer.addRoute(new MyHandler());
```

这会立即注册路由，无需重启服务器。

## 路由管理方法

### 获取所有路由信息

```java
RouteRegistry.getAllRoutes().forEach((path, handler) -> {
    System.out.println("Path: " + path);
    System.out.println("Method: " + handler.getMethod());
    System.out.println("Description: " + handler.getDescription());
});
```

### 检查路由是否存在

```java
RouteHandler handler = RouteRegistry.get("/status");
if (handler != null) {
    // 路由存在
}
```

## 扩展示例

### 1. 带参数的路由

```java
public class PlayerHandler implements RouteHandler {
    
    @Override
    public String getPath() {
        return "/player";
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String playerName = extractParam(query, "name");
        
        String response = String.format(
            "{\"player\": \"%s\", \"online\": true}",
            playerName
        );
        
        // ... 发送响应
    }
    
    private String extractParam(String query, String param) {
        if (query == null) return "";
        String[] params = query.split("&");
        for (String p : params) {
            String[] keyValue = p.split("=");
            if (keyValue.length == 2 && keyValue[0].equals(param)) {
                return keyValue[1];
            }
        }
        return "";
    }
}
```

### 2. POST请求处理器

```java
public class CommandHandler implements RouteHandler {
    
    @Override
    public String getPath() {
        return "/command";
    }
    
    @Override
    public String getMethod() {
        return "POST";
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 读取请求体
        String body = new String(exchange.getRequestBody().readAllBytes());
        
        // 处理命令...
        
        String response = "{\"success\": true}";
        exchange.sendResponseHeaders(200, response.getBytes().length);
        // ...
    }
}
```

## 最佳实践

1. **保持处理器简单**：每个处理器只做一件事
2. **使用有意义的命名**：类名和路径应该清晰表达功能
3. **添加描述**：为每个路由提供清晰的描述
4. **错误处理**：始终处理可能的异常情况
5. **资源管理**：使用try-with-resources自动关闭流
6. **日志记录**：适当记录请求和错误信息

## 调试技巧

查看服务器启动时的日志：
```
[WebAPI] Available routes:
[WebAPI]   - / (GET): Returns basic information about the WebAPI
[WebAPI]   - /status (GET): Returns server status and version information
[WebAPI]   - /myendpoint (GET): My custom endpoint description
```

这会显示所有已注册的路由及其描述。
