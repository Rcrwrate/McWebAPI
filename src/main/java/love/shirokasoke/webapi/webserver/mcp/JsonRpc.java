package love.shirokasoke.webapi.webserver.mcp;

import static love.shirokasoke.webapi.Constant.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * JSON-RPC 2.0 编解码辅助。
 */
public final class JsonRpc {

    private JsonRpc() {}

    // region 标准错误码
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    public static ObjectNode success(JsonNode id, JsonNode result) {
        ObjectNode node = mapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.set("id", id == null || id.isNull() ? mapper.nullNode() : id);
        node.set("result", result == null ? mapper.createObjectNode() : result);
        return node;
    }

    public static ObjectNode error(JsonNode id, int code, String message) {
        return error(id, code, message, null);
    }

    public static ObjectNode error(JsonNode id, int code, String message, JsonNode data) {
        ObjectNode node = mapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.set("id", id == null || id.isNull() ? mapper.nullNode() : id);
        ObjectNode err = node.putObject("error");
        err.put("code", code);
        err.put("message", message);
        if (data != null) err.set("data", data);
        return node;
    }

    /** 判断是否为「通知」（无 id 字段）：通知不应产生响应体 */
    public static boolean isNotification(JsonNode request) {
        return request == null || !request.has("id")
            || request.get("id")
                .isNull();
    }
}
