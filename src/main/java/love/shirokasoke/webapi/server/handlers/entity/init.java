package love.shirokasoke.webapi.server.handlers.entity;

import love.shirokasoke.webapi.server.RouteRegistry;

public class init {

    public static void i() {
        RouteRegistry.register(new EntitiesHandler());
        RouteRegistry.register(new EntityHandler());
    }
}
