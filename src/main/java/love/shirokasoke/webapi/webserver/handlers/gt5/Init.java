package love.shirokasoke.webapi.webserver.handlers.gt5;

import love.shirokasoke.webapi.config.ServerConfig;
import love.shirokasoke.webapi.webserver.RouteRegistry;

public class Init {

    public static void i() {
        RouteRegistry.register(new GT5BaseHandler());
        RouteRegistry.register(new GT5StatusHandler());
        RouteRegistry.register(new GT5ChunkScanHandler());
        RouteRegistry.register(new GT5BatchHandler());
        RouteRegistry.register(new GT5VeinHandler());
        RouteRegistry.register(new GT5VeinListHandler());
        RouteRegistry.register(new GT5VeinDefHandler());
        if (ServerConfig.useVirtualThreads && ServerConfig.useServerSideEvent) {
            RouteRegistry.register(new GT5BatchSSEHandler());
        }
    }
}
