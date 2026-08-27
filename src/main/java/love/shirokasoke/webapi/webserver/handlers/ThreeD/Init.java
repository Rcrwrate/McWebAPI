package love.shirokasoke.webapi.webserver.handlers.ThreeD;

import love.shirokasoke.webapi.webserver.RouteRegistry;

public class Init {

    public static void i() {
        RouteRegistry.register(new PlayerPrintHandler());
        RouteRegistry.register(new WorldPrintHandler());
    }
}
