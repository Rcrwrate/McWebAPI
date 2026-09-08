package love.shirokasoke.webapi.webserver.handlers.test;

import love.shirokasoke.webapi.config.DebugConfig;
import love.shirokasoke.webapi.webserver.RouteRegistry;
import love.shirokasoke.webapi.webserver.handlers.TestHandler;

public class Init {

    public static void i() {
        if (DebugConfig.test) {
            RouteRegistry.register(new TestHandler());
            RouteRegistry.register(new NBTTestHandler());
        }
    }
}
