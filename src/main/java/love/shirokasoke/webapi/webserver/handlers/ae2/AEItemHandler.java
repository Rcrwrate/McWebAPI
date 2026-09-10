package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;
import java.util.UUID;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.webserver.Context;
import love.shirokasoke.webapi.webserver.handlers.ae2.AEItem.CacheEntry;

public class AEItemHandler extends AEBaseHandler {

    @Override
    public String getPath() {
        return "/ae/item";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        Context context = AEinit(exchange);
        UUID gridId = context.grid.getId();
        CacheEntry entry = AEItem.getOrCreateCacheEntry(gridId, context.grid);
        exchange.getResponseHeaders()
            .set("Content-Type", "application/json");
        sendResponse(exchange, 200, entry.jsonBytes);
    }

}
