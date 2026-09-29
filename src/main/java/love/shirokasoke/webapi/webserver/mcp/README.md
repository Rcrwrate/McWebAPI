# 内置 MCP 服务端

## 开启方式

配置文件 `config/shirokasoke/WebAPI.cfg`，`[server.mcp]` 段：

| 配置项            | 默认         | 说明                                         |
| ----------------- | ------------ | -------------------------------------------- |
| `enable`          | `false`      | 是否注册 `/mcp` 路由                         |
| `allowWrite`      | `false`      | 是否暴露写工具（setblock / 强制加载区块 等） |
| `protocolVersion` | `2025-03-26` | 服务端对外声明的协议版本                     |

> 写工具默认关闭, AI Agent 不应在无人监督下修改世界。

## 客户端配置示例

```json
{
  "mcpServers": {
    "mcwebapi": {
      "type": "http",
      "url": "http://127.0.0.1:40002/mcp"
      "headers": {
        "Authorization": "Bearer YOUR_TOKEN",
      }
    }
  }
}
```
