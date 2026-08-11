package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;

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
    public String getDescription() {
        return "AE Base";
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
        String query = exchange.getRequestURI()
            .getQuery();
        Context context = new Context(getCoordinates(query)).initServer()
            .initWorld()
            .checkblockExists()
            .initTileEntity()
            .initAE();
        return context;
    }
}
