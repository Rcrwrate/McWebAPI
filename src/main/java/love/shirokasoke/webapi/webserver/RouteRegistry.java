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
        register(new love.shirokasoke.webapi.webserver.handlers.VersionHandler());
        register(
            new love.shirokasoke.webapi.webserver.handlers.StaticFileHandler(
                "/static/favicon.ico",
                "image/x-icon",
                "/favicon.ico"));

        register(new love.shirokasoke.webapi.webserver.handlers.TPSHandler());
        love.shirokasoke.webapi.webserver.handlers.test.Init.i();
        love.shirokasoke.webapi.webserver.handlers.block.Init.i();
        love.shirokasoke.webapi.webserver.handlers.item.Init.i();
        love.shirokasoke.webapi.webserver.handlers.chunk.Init.i();
        love.shirokasoke.webapi.webserver.handlers.entity.Init.i();
        love.shirokasoke.webapi.webserver.handlers.ae2.Init.i();
        love.shirokasoke.webapi.webserver.handlers.fluid.Init.i();
        love.shirokasoke.webapi.webserver.handlers.gt5.Init.i();
        love.shirokasoke.webapi.webserver.handlers.recipe.Init.i();
        love.shirokasoke.webapi.webserver.handlers.ThreeD.Init.i();
        register(new love.shirokasoke.webapi.webserver.handlers.ProfilerHandler());
        register(new love.shirokasoke.webapi.webserver.handlers.LagAnalyzerHandler());
        register(new love.shirokasoke.webapi.webserver.handlers.WorldInfoHandler());

        register(new love.shirokasoke.webapi.webserver.handlers.NotFoundHandler());
    }
}
