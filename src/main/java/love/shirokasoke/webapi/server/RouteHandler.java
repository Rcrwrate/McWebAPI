package love.shirokasoke.webapi.server;

import java.io.IOException;
import java.io.OutputStream;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import love.shirokasoke.webapi.Constant;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.log;

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
        long startTime = System.currentTimeMillis();
        String method = exchange.getRequestMethod();
        String uri = exchange.getRequestURI()
            .toString();

        if (!Auth.auth(
            uri,
            method,
            exchange.getRequestHeaders()
                .get("Authorization"))) {
            sendErrorResponse(exchange, 401, "not auth");
            return;
        }
        try {
            run(exchange);
            long duration = System.currentTimeMillis() - startTime;
            MyMod.LOG.info("[{}]\t{} - {}ms", method, uri, duration); // 缺一个来源IP地址
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            if (e instanceof Error) {
                Error e2 = (Error) e;
                sendErrorResponse(exchange, e2.code, e2.getMessage());
                MyMod.LOG.error("[{}]\t{} - Error after {}ms\t{}", method, uri, duration, e2.getMessage());
                return;
            }

            MyMod.LOG.error("[{}]\t{} - Error after {}ms", method, uri, duration);
            log.e(e);
            sendErrorResponse(
                exchange,
                500,
                e.getMessage() != null ? e.getMessage()
                    : e.getClass()
                        .getSimpleName());
        }
    }

    default void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        exchange.getResponseHeaders()
            .set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, message.getBytes().length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }

    default void sendResponse(HttpExchange exchange, int statusCode, Object json) throws IOException {
        String message = mapper.writeValueAsString(json);
        exchange.getResponseHeaders()
            .set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, message.getBytes().length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }

    default void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        String response = String.format("{\"error\": \"%s\"}", message);
        sendResponse(exchange, statusCode, response);
    }

    public class coordinates extends ChunkCoordinates {

        public int dimension;

        public coordinates(int x, int y, int z, int dimension) {
            super(x, y, z);
            this.dimension = dimension;
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
        String query = exchange.getRequestURI()
            .getQuery();
        return parseQueryParams(query);
    }

    public class Error extends IOException {

        public int code;

        public Error(int code, String message) {
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

    default public MinecraftServer getServer() throws Error {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) {
            throw new Error(503, "Server not available");
        }
        return server;
    }
}
