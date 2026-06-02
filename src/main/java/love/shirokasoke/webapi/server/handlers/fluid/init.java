package love.shirokasoke.webapi.server.handlers.fluid;

import love.shirokasoke.webapi.server.RouteRegistry;

public class init {

    public static void i() {
        RouteRegistry.register(new FluidsHandler());
        RouteRegistry.register(new FluidContainersHandler());
        RouteRegistry.register(new FluidIconHandler());
    }
}
