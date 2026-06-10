package love.shirokasoke.webapi.webserver.handlers.fluid;

import love.shirokasoke.webapi.webserver.RouteRegistry;

public class init {

    public static void i() {
        RouteRegistry.register(new FluidsHandler());
        RouteRegistry.register(new FluidContainersHandler());
        RouteRegistry.register(new FluidIconHandler());
    }
}
