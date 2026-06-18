package love.shirokasoke.webapi.webserver.handlers.gt5;

import love.shirokasoke.webapi.webserver.RouteRegistry;

public class init {

    public static void i() {
        RouteRegistry.register(new GT5BaseHandler());
        RouteRegistry.register(new GT5ChunkScanHandler());
        RouteRegistry.register(new GT5BatchHandler());
    }
}
