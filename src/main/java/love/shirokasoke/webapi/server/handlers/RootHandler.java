package love.shirokasoke.webapi.server.handlers;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.Tags;
import love.shirokasoke.webapi.server.RouteHandler;

/**
 * Root route handler - Returns basic information about the WebAPI
 */
public class RootHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/";
    }

    @Override
    public String getDescription() {
        return "Returns basic information about the WebAPI";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        String response = String.format(
            "{\"message\": \"Minecraft WebAPI is running\", \"modid\": \"%s\",\"version\": \"%s\"}",
            MyMod.MODID,
            Tags.VERSION);

        setCache(exchange, 86400);
        sendResponse(exchange, 200, response);
    }
}
