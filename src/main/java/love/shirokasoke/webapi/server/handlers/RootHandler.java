package love.shirokasoke.webapi.server.handlers;

import java.io.IOException;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
        ObjectNode response = mapper.createObjectNode();
        response.put("modid", MyMod.MODID);
        response.put("version", Tags.VERSION);

        setCache(exchange, 86400);
        sendResponse(exchange, response);
    }
}
