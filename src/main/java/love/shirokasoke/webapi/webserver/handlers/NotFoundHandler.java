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
public class NotFoundHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/";
    }

    @Override
    public String getDescription() {
        return "Returns 404";
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
        throw new ApiException(404, mapper.writeValueAsString(response));
    }
}
