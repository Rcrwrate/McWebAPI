package love.shirokasoke.webapi.server;

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
        register(new love.shirokasoke.webapi.server.handlers.RootHandler());
        register(
            new love.shirokasoke.webapi.server.handlers.StaticFileHandler(
                "static/favicon.ico",
                "image/x-icon",
                "/favicon.ico"));

        register(new love.shirokasoke.webapi.server.handlers.TPSHandler());
        register(new love.shirokasoke.webapi.server.handlers.TestHandler());
        love.shirokasoke.webapi.server.handlers.block.init.i();
        love.shirokasoke.webapi.server.handlers.item.init.i();
        love.shirokasoke.webapi.server.handlers.chunk.init.i();

        register(new love.shirokasoke.webapi.server.handlers.ProfilerHandler());
        register(new love.shirokasoke.webapi.server.handlers.LagAnalyzerHandler());
        register(new love.shirokasoke.webapi.server.handlers.WorldInfoHandler());
    }
}
