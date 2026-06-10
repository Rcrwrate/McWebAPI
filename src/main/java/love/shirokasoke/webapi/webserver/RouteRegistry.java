package love.shirokasoke.webapi.webserver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Route registry for WebAPI Register all route handlers here
 */
public class RouteRegistry {

    private static final Map<String, RouteHandler> routes = new LinkedHashMap<>();

    /**
     * Register a route handler
     */
    public static void register(RouteHandler handler) {
        routes.put(handler.getPath(), handler);
    }

    /**
     * Get all registered routes
     */
    public static Map<String, RouteHandler> getAllRoutes() {
        return routes;
    }

    /**
     * Get a specific route handler
     */
    public static RouteHandler get(String path) {
        return routes.get(path);
    }

    /**
     * Initialize and register all default routes
     */
    public static void initializeDefaultRoutes() {
        register(new love.shirokasoke.webapi.webserver.handlers.RootHandler());
        register(
            new love.shirokasoke.webapi.webserver.handlers.StaticFileHandler(
                "/static/favicon.ico",
                "image/x-icon",
                "/favicon.ico"));

        register(new love.shirokasoke.webapi.webserver.handlers.TPSHandler());
        register(new love.shirokasoke.webapi.webserver.handlers.TestHandler());
        love.shirokasoke.webapi.webserver.handlers.block.init.i();
        love.shirokasoke.webapi.webserver.handlers.item.init.i();
        love.shirokasoke.webapi.webserver.handlers.chunk.init.i();
        love.shirokasoke.webapi.webserver.handlers.entity.init.i();
        love.shirokasoke.webapi.webserver.handlers.ae2.init.i();
        love.shirokasoke.webapi.webserver.handlers.fluid.init.i();
        register(new love.shirokasoke.webapi.webserver.handlers.ProfilerHandler());
        register(new love.shirokasoke.webapi.webserver.handlers.LagAnalyzerHandler());
        register(new love.shirokasoke.webapi.webserver.handlers.WorldInfoHandler());
    }
}
