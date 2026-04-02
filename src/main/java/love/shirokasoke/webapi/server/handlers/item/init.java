package love.shirokasoke.webapi.server.handlers.item;

import love.shirokasoke.webapi.server.RouteRegistry;

public class init {

    public static void i() {
        RouteRegistry.register(new ItemHandler());
        RouteRegistry.register(new ItemsHandler());
    }
}
