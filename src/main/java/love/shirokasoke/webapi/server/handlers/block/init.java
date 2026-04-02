package love.shirokasoke.webapi.server.handlers.block;

import love.shirokasoke.webapi.server.RouteRegistry;

public class init {

    public static void i() {
        RouteRegistry.register(new SetBlockHandler());
        RouteRegistry.register(new BlockHandler());
        RouteRegistry.register(new AEHandler());
        RouteRegistry.register(new BlocksHandler());
    }
}
