package love.shirokasoke.webapi.webserver.mcp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static love.shirokasoke.webapi.Constant.mapper;

import java.net.URI;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.Tags;
import love.shirokasoke.webapi.webserver.RouteHandler;
import love.shirokasoke.webapi.webserver.RouteRegistry;

/**
 * MCP（Model Context Protocol）
 */
public final class MCP {

    /** 支持的协议版本，按新→旧排列，取与客户端交集 */
    public static final String[] SUPPORTED_PROTOCOL_VERSIONS = { "2025-06-18", "2025-03-26", "2024-11-05" };

    /** 默认回落到的最新稳定版（可被配置覆盖） */
    public static String defaultProtocolVersion() {
        String cfg = love.shirokasoke.webapi.config.McpConfig.protocolVersion;
        return cfg == null || cfg.isEmpty() ? "2025-03-26" : cfg;
    }

    public static final String SERVER_NAME = MyMod.MODID;
    public static final String SERVER_VERSION = Tags.VERSION;

    private MCP() {}

    // region main

    /**
     * 处理一条 JSON-RPC 消息（可能是单条，也可能是 batch 数组）。
     *
     * @return 响应 JsonNode；若全部是通知（无 id）则返回 {@code null}
     */
    public static JsonNode handleMessage(JsonNode request) {
        if (request == null || request.isNull()) {
            return JsonRpc.error(null, JsonRpc.INVALID_REQUEST, "Empty request");
        }
        // batch 支持
        if (request.isArray()) {
            ArrayNode responses = mapper.createArrayNode();
            for (JsonNode item : request) {
                JsonNode r = handleSingle(item);
                if (r != null) responses.add(r);
            }
            return responses.isEmpty() ? null : responses;
        }
        return handleSingle(request);
    }

    private static JsonNode handleSingle(JsonNode request) {
        if (!request.isObject()) {
            return JsonRpc.error(null, JsonRpc.INVALID_REQUEST, "Request must be a JSON object");
        }
        JsonNode id = request.get("id");
        boolean notification = JsonRpc.isNotification(request);

        String method = textOrNull(request.get("method"));
        if (method == null) {
            return notification ? null : JsonRpc.error(id, JsonRpc.INVALID_REQUEST, "Missing method");
        }

        try {
            return switch (method) {
                case "initialize" -> wrap(id, notification, initialize(request.get("params")));
                case "notifications/initialized", "initialized" -> null; // 纯通知
                case "ping" -> wrap(id, notification, mapper.createObjectNode());
                case "tools/list" -> wrap(id, notification, toolsList());
                case "tools/call" -> wrap(id, notification, toolsCall(request.get("params")));
                default -> notification ? null
                    : JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Method not found: " + method);
            };
        } catch (ToolCallException e) {
            return notification ? null
                : JsonRpc.error(
                    id,
                    JsonRpc.INVALID_PARAMS,
                    e.getMessage(),
                    mapper.createObjectNode()
                        .put("httpStatus", e.httpStatus));
        } catch (Throwable t) {
            MyMod.LOG.error("[MCP] method {} failed", method, t);
            return notification ? null : JsonRpc.error(id, JsonRpc.INTERNAL_ERROR, String.valueOf(t.getMessage()));
        }
    }

    private static JsonNode wrap(JsonNode id, boolean notification, JsonNode result) {
        if (notification) return null;
        return JsonRpc.success(id, result);
    }

    // region initialize

    private static JsonNode initialize(JsonNode params) {
        String clientVersion = params != null ? textOrNull(params.get("protocolVersion")) : null;
        String negotiated = negotiate(clientVersion);

        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", negotiated);
        ObjectNode caps = result.putObject("capabilities");
        caps.putObject("tools")
            .put("listChanged", false);
        ObjectNode info = result.putObject("serverInfo");
        info.put("name", SERVER_NAME);
        info.put("version", SERVER_VERSION);
        if (clientVersion != null && !clientVersion.equals(negotiated)) {
            MyMod.LOG.info("[MCP] Client protocol {} not supported, negotiated down to {}", clientVersion, negotiated);
        }
        return result;
    }

    private static String negotiate(String clientVersion) {
        if (clientVersion == null || clientVersion.isEmpty()) return defaultProtocolVersion();
        for (String v : SUPPORTED_PROTOCOL_VERSIONS) {
            if (v.equals(clientVersion)) return v;
        }
        // 未知版本：回落到默认（MCP 允许服务端选择自己支持的版本）
        return defaultProtocolVersion();
    }

    // region tools/list

    private static JsonNode toolsList() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        for (McpTool tool : McpRegistry.tools()) {
            tools.add(tool.toMcpDescriptor());
        }
        return result;
    }

    // region tools/call

    private static JsonNode toolsCall(JsonNode params) throws Exception {
        if (params == null) throw new ToolCallException(400, "Missing params");
        String name = textOrNull(params.get("name"));
        if (name == null) throw new ToolCallException(400, "Missing tool name");

        McpTool tool = McpRegistry.get(name);
        if (tool == null) {
            throw new ToolCallException(404, "Unknown tool: " + name);
        }
        if (!McpRegistry.isToolEnabled(tool)) {
            throw new ToolCallException(403, "Tool is disabled by server config: " + name);
        }

        JsonNode args = params.get("arguments");
        return invoke(tool, args == null || args.isNull() ? mapper.createObjectNode() : args);
    }

    /**
     * 把工具调用翻译成一次内存 HTTP 交换并执行。
     */
    public static JsonNode invoke(McpTool tool, JsonNode args) throws Exception {
        Map<String, String> query = McpRegistry.buildQuery(tool, args);
        byte[] body = McpRegistry.buildBody(tool, args);

        String path = tool.route;
        URI uri = URI.create(buildUriString(path, query));

        // 少数路由同时支持 POST(提交) 与 GET(轮询)，按是否提供轮询参数自动选择方法
        String httpMethod = tool.method;
        if (tool.method.equals("POST") && query.containsKey("jobId")) {
            httpMethod = "GET";
        }

        MemoryExchange exchange = new MemoryExchange(httpMethod, uri, body, McpRegistry.requestHeaders(tool));
        RouteHandler handler = RouteRegistry.get(path);
        if (handler == null) {
            // 路由可能被 disabledRoutes 移除
            throw new ToolCallException(404, "Route not registered: " + path);
        }

        // 走完整 handle：CORS → 鉴权 → run → 错误映射
        handler.handle(exchange);

        int status = exchange.getStatusCode();
        byte[] bytes = exchange.getResponseBodyBytes();
        String contentType = exchange.getResponseHeader("Content-Type");

        ObjectNode result = mapper.createObjectNode();
        result.put("isError", status < 200 || status >= 300);

        ArrayNode content = result.putArray("content");
        if (isImage(contentType)) {
            ObjectNode image = content.addObject();
            image.put("type", "image");
            image.put(
                "data",
                java.util.Base64.getEncoder()
                    .encodeToString(bytes));
            image.put("mimeType", contentType == null ? "image/png" : contentType.split(";")[0].trim());
        } else {
            ObjectNode textNode = content.addObject();
            textNode.put("type", "text");
            textNode.put("text", formatPayload(status, bytes));
        }

        // 结构化输出（MCP 2025-06-18 起支持）
        if (isJson(contentType)) {
            try {
                result.set(
                    "structuredContent",
                    mapper.createObjectNode()
                        .put("status", status)
                        .set("data", mapper.readTree(bytes)));
            } catch (Throwable ignore) {}
        }

        return result;
    }

    // region something else

    private static String formatPayload(int status, byte[] bytes) {
        String body = new String(bytes, UTF_8);
        if (body.isEmpty()) return "HTTP " + status;
        return body;
    }

    private static String buildUriString(String path, Map<String, String> query) {
        if (query == null || query.isEmpty()) return path;
        StringBuilder sb = new StringBuilder(path).append('?');
        boolean first = true;
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (!first) sb.append('&');
            first = false;
            sb.append(e.getKey())
                .append('=')
                .append(e.getValue() == null ? "" : e.getValue());
        }
        return sb.toString();
    }

    private static boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    private static boolean isJson(String contentType) {
        return contentType != null && contentType.contains("json");
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    /** 工具调用失败（会映射成 JSON-RPC INVALID_PARAMS） */
    public static class ToolCallException extends Exception {

        private static final long serialVersionUID = 1L;

        public final int httpStatus;

        public ToolCallException(int httpStatus, String message) {
            super(message);
            this.httpStatus = httpStatus;
        }
    }
}
