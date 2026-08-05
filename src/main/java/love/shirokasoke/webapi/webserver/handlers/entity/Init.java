package love.shirokasoke.webapi.webserver.handlers.entity;

import love.shirokasoke.webapi.webserver.RouteRegistry;

public class Init {

    public static void i() {
        RouteRegistry.register(new EntitiesHandler());
        RouteRegistry.register(new EntityHandler());
    }
}
