package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.util.UUID;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.config.AE2Config;
import love.shirokasoke.webapi.webserver.Context;
import love.shirokasoke.webapi.webserver.SSEClient;
import love.shirokasoke.webapi.webserver.SSEHandler;
import love.shirokasoke.webapi.webserver.handlers.ae2.AEItem.CacheEntry;

public class AEItemSSEHandler implements SSEHandler {

    private final int sleep = AE2Config.item.interval * 1000;

    @Override
    public String getPath() {
        return "/ae/item/sse";
    }

    @Override
    public void run(HttpExchange exchange, SSEClient client) throws Exception {
        Context context = new Context(getCoordinates(exchange)).initServer()
            .initWorld()
            .checkblockExists()
            .initTileEntity()
            .initAE();
        UUID gridId = context.grid.getId();

        while (client.isOpen()) {
            CacheEntry data = AEItem.getOrCreateCacheEntry(gridId, context.grid);
            client.event("aeitem", data.jsonBytes);
            Thread.sleep(sleep);
            client.heartbeat();
            if (!client.isOpen()) {
                data.future.cancel(false);
            }
        }

    }
}
