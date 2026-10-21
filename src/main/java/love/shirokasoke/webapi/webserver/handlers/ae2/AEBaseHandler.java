package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.webserver.Context;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class AEBaseHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/ae";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        AEinit(exchange);
        ObjectNode response = mapper.createObjectNode()
            .put("message", "AE HIT");

        setCache(exchange, 86400);
        sendResponse(exchange, response);
    }

    protected Context AEinit(HttpExchange exchange) throws ApiException {
        final Context context = new Context(getCoordinates(exchange)).initServer()
            .initWorld()
            .checkblockExists()
            .initTileEntity()
            .initAE();
        return context;
    }

    protected Context AEinit(Map<String, String> query) throws ApiException {
        final Context context = new Context(getCoordinates(query)).initServer()
            .initWorld()
            .checkblockExists()
            .initTileEntity()
            .initAE();
        return context;
    }
}
