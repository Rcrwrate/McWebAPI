package love.shirokasoke.webapi.server.handlers.ae2;

import love.shirokasoke.webapi.server.RouteRegistry;

public class init {

    public static void i() {
        RouteRegistry.register(new AEBaseHandler());
        RouteRegistry.register(new AEItemHandler());
        RouteRegistry.register(new AEMEHandler());
    }
}
