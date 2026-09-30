package love.shirokasoke.webapi.webserver.mcp;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.webserver.RouteHandler;

public class McpHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/mcp";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        setNoCache(exchange);

        if (!"POST".equals(method)) {
            // GET 用于 SSE 流，未实现；DELETE 用于结束会话，同样未实现
            throw new ApiException(405, "Only POST is supported on /mcp in this version");
        }

        JsonNode request;
        try {
            request = getBody(exchange);
        } catch (IOException e) {
            sendResponse(
                exchange,
                200,
                JsonRpc.error(null, JsonRpc.PARSE_ERROR, "Parse error: " + e.getMessage()),
                true);
            return;
        }

        if (request == null || request.isMissingNode()) {
            sendResponse(exchange, 200, JsonRpc.error(null, JsonRpc.PARSE_ERROR, "Empty body"), true);
            return;
        }

        JsonNode response = MCP.handleMessage(request);

        if (response == null) {
            // 全部是通知：按规范回 202，无响应体
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }

        sendResponse(exchange, 200, response, true);
    }
}
