package love.shirokasoke.webapi.webserver.handlers.test;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.webserver.RouteRegistry;
import love.shirokasoke.webapi.webserver.handlers.TestHandler;

public class Init {

    public static void i() {
        if (Config.test) {
            RouteRegistry.register(new TestHandler());
            RouteRegistry.register(new NBTTestHandler());
        }
    }
}
