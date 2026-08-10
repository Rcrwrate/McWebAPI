package love.shirokasoke.webapi.webserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import net.minecraft.util.ChunkCoordinates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import codechicken.lib.vec.BlockCoord;
import love.shirokasoke.webapi.Constant;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.Logs;
import love.shirokasoke.webapi.webserver.Auth.Auth;

/**
 * Route handler interface for WebAPI Implement this interface to create new API
 * endpoints
 */
public interface RouteHandler extends HttpHandler {

    public ObjectMapper mapper = Constant.mapper;

    /**
     * Get the path for this route Example: "/status", "/players", etc.
     */
    String getPath();

    /**
     * Get description of this route for documentation
     */
    default String getDescription() {
        return "No description available";
    }

    void run(HttpExchange exchange) throws Exception;

    @Override
    public default void handle(HttpExchange exchange) throws IOException {
        long startTime = System.nanoTime();
        String method = exchange.getRequestMethod();
        String uri = exchange.getRequestURI()
            .toString();

        setCorsHeaders(exchange);
        // 处理 OPTIONS 预检请求
        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!Auth.auth(
            uri,
            method,
            exchange.getRequestHeaders()
                .get("Authorization"))) {
            sendErrorResponse(exchange, 401, "not auth", null);
            return;
        }
        try {
            run(exchange);
            double duration = (System.nanoTime() - startTime) / 1_000_000.0;
            MyMod.LOG.info("[{}]\t{} - {}ms", method, uri, String.format("%.3f", duration)); // 缺一个来源IP地址
        } catch (Throwable e) {
            double duration = (System.nanoTime() - startTime) / 1_000_000.0;
            if (e instanceof ApiException) {
                ApiException e2 = (ApiException) e;
                sendErrorResponse(exchange, e2.code, e2.getMessage(), null);
                MyMod.LOG.error(
                    "[{}]\t{} - Error after {}ms\t{}",
                    method,
                    uri,
                    String.format("%.3f", duration),
                    e2.getMessage());
                return;
            }

            MyMod.LOG.error("[{}]\t{} - Error after {}ms", method, uri, String.format("%.3f", duration));
            sendErrorResponse(
                exchange,
                500,
                e.getMessage() != null ? e.getMessage()
                    : e.getClass()
                        .getSimpleName(),
                Logs.e(e));
        }
    }

    default void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders()
            .set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders()
            .set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        exchange.getResponseHeaders()
            .set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders()
            .set("Access-Control-Max-Age", "86400");
        exchange.getResponseHeaders()
            .set("X-Powered-By", "love.shirokasoke.webapi");
    }

    /**
     * 对体积超过 {@link Compressor#THRESHOLD} 的响应，
     * 按客户端 {@code Accept-Encoding} 自适应选择压缩
     * 
     * @apiNote 必须在此之前设置 <b>Content-Type</b>
     */
    default void sendResponse(HttpExchange exchange, int statusCode, byte[] bytes) throws IOException {
        byte[] body = bytes;
        String encoding = Compressor.fastCheck(exchange, bytes.length);
        if (encoding != null) {
            try {
                byte[] compressed = Compressor.compress(encoding, bytes);
                MyMod.LOG.debug("encoding:\t{} {}/{}", encoding, compressed.length, bytes.length);
                if (compressed.length < bytes.length) {
                    body = compressed;
                    exchange.getResponseHeaders()
                        .set("Content-Encoding", encoding);
                    exchange.getResponseHeaders()
                        .add("Vary", "Accept-Encoding");
                }
            } catch (Throwable t) {
                Logs.e(t);
            }
        }
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
        exchange.close();
    }

    default void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        exchange.getResponseHeaders()
            .set("Content-Type", "application/json");
        sendResponse(exchange, statusCode, message.getBytes(StandardCharsets.UTF_8));
    }

    default void sendResponse(HttpExchange exchange, int statusCode, Object json, boolean direct) throws IOException {
        exchange.getResponseHeaders()
            .set("Content-Type", "application/json");
        sendResponse(exchange, statusCode, mapper.writeValueAsBytes(json));
    }

    default void sendResponse(HttpExchange exchange, ObjectNode json) throws IOException {
        exchange.getResponseHeaders()
            .set("Content-Type", "application/json");
        sendResponse(
            exchange,
            200,
            mapper.writeValueAsBytes(
                mapper.createObjectNode()
                    .put("success", true)
                    .set("data", json)));
    }

    default void sendResponse(HttpExchange exchange, ArrayNode json) throws IOException {
        exchange.getResponseHeaders()
            .set("Content-Type", "application/json");
        sendResponse(
            exchange,
            200,
            mapper.writeValueAsBytes(
                mapper.createObjectNode()
                    .put("success", true)
                    .set("data", json)));
    }

    default void sendResponse(HttpExchange exchange, byte[] data) throws IOException {
        sendResponse(exchange, 200, data);
    }

    default void sendErrorResponse(HttpExchange exchange, int statusCode, String message, String stack)
        throws IOException {
        exchange.getResponseHeaders()
            .set("Content-Type", "application/json");
        sendResponse(
            exchange,
            statusCode,
            mapper.writeValueAsBytes(
                (mapper.createObjectNode()
                    .put("success", false)
                    .put("message", message)
                    .put("stack", stack))));
    }

    public class coordinates extends ChunkCoordinates {

        public int dimension;

        public coordinates(int x, int y, int z, int dimension) {
            super(x, y, z);
            this.dimension = dimension;
        }

        public BlockCoord BlockCoord() {
            return new BlockCoord(this.posX, this.posY, this.posZ);
        }
    }

    default coordinates getCoordinates(String query) {
        int x = 0, y = 0, z = 0;
        int dimension = 0; // 默认主世界
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length != 2) continue;

            String key = keyValue[0];
            String value = keyValue[1];

            switch (key) {
                case "x":
                    x = Integer.parseInt(value);
                    break;
                case "y":
                    y = Integer.parseInt(value);
                    break;
                case "z":
                    z = Integer.parseInt(value);
                    break;
                case "dim":
                case "dimension":
                    dimension = Integer.parseInt(value);
                    break;
            }
        }
        return new coordinates(x, y, z, dimension);
    }

    /**
     * 解析 URL 查询参数为键值对
     * 
     * @param query 查询字符串 (例如: "id=1&name=test")
     * @return 包含所有参数的 Map
     */
    default java.util.Map<String, String> parseQueryParams(String query) {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                String key = java.net.URLDecoder.decode(keyValue[0], java.nio.charset.StandardCharsets.UTF_8);
                String value = java.net.URLDecoder.decode(keyValue[1], java.nio.charset.StandardCharsets.UTF_8);
                params.put(key, value);
            } else if (keyValue.length == 1) {
                String key = java.net.URLDecoder.decode(keyValue[0], java.nio.charset.StandardCharsets.UTF_8);
                params.put(key, "");
            }
        }
        return params;
    }

    /**
     * 从 HttpExchange 中获取并解析查询参数
     * 
     * @param exchange HTTP 交换对象
     * @return 包含所有参数的 Map
     */
    default java.util.Map<String, String> parseQueryParams(HttpExchange exchange) {
        // 必须使用 getRawQuery(): getQuery() 已做过一次 percent-decode,
        // 再经 URLDecoder 二次解码会把 base64 中的 '+' 变成空格 (0x20)
        String query = exchange.getRequestURI()
            .getRawQuery();
        return parseQueryParams(query);
    }

    public final class ApiException extends IOException {

        public int code;

        public ApiException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    /**
     * 从 HttpExchange 中读取并解析请求体为 JsonNode
     * 
     * @param exchange HTTP 交换对象
     * @return 解析后的 JsonNode
     * @throws IOException 如果读取或解析失败
     */
    default public JsonNode getBody(HttpExchange exchange) throws IOException {
        try (java.io.InputStream is = exchange.getRequestBody()) {
            return mapper.readTree(is);
        }
    }

    /**
     * 从 HttpExchange 中读取并解析请求体为指定类的实例
     * 
     * @param exchange HTTP 交换对象
     * @param clazz    目标类的 Class 对象
     * @return 解析后的类实例
     * @throws IOException 如果读取或解析失败
     */
    default public <T> T getBody(HttpExchange exchange, Class<T> clazz) throws IOException {
        try (java.io.InputStream is = exchange.getRequestBody()) {
            return mapper.readValue(is, clazz);
        }
    }

    default public void setNoCache(HttpExchange exchange) {
        exchange.getResponseHeaders()
            .set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.getResponseHeaders()
            .set("Pragma", "no-cache");
        exchange.getResponseHeaders()
            .set("Expires", "0");
    }

    default public void setCache(HttpExchange exchange, int time) {
        exchange.getResponseHeaders()
            .set("Cache-Control", "public, max-age=" + time);
        exchange.getResponseHeaders()
            .set(
                "Expires",
                DateTimeFormatter.RFC_1123_DATE_TIME.format(
                    ZonedDateTime.now()
                        .plusSeconds(time)));
    }
}
