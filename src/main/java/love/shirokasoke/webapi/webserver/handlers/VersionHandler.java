package love.shirokasoke.webapi.webserver.handlers;

import java.io.IOException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.Tags;
import love.shirokasoke.webapi.thread.UpdateChecker;
import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * Root route handler - Returns basic information about the WebAPI
 */
public class VersionHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/version";
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

        java.time.Instant buildTime = UpdateChecker.readLocalBuildTime();
        if (buildTime != null) {
            response.put("buildTime", buildTime.getEpochSecond());
        }

        setCache(exchange, 86400);
        sendResponse(exchange, response);
    }
}
