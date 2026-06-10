package love.shirokasoke.webapi.webserver.handlers.item;

import love.shirokasoke.webapi.webserver.RouteRegistry;

public class init {

    public static void i() {
        RouteRegistry.register(new AEHandler());
        RouteRegistry.register(new ItemHandler());
        RouteRegistry.register(new ItemsHandler());
        RouteRegistry.register(new ItemIconHandler());
    }
}
