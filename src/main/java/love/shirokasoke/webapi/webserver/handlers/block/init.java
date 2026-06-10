package love.shirokasoke.webapi.webserver.handlers.block;

import love.shirokasoke.webapi.webserver.RouteRegistry;

public class init {

    public static void i() {
        RouteRegistry.register(new SetBlockHandler());
        RouteRegistry.register(new FMPHandler());
        RouteRegistry.register(new BlockHandler());
        RouteRegistry.register(new BlocksHandler());
    }
}
