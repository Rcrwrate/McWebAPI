package love.shirokasoke.webapi.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import love.shirokasoke.webapi.MyMod;

/**
 * WebServer - HTTP Server for WebAPI
 * Manages HTTP server lifecycle and route registration
 */
public class WebServer {

    private static HttpServer server;
    private static boolean isRunning = false;

    /**
     * Start the HTTP server on the specified port
     * Automatically registers all routes from RouteRegistry
     */
    public static void start(int port, int nThreads) {
        if (isRunning) {
            MyMod.LOG.warn("[WebServer] Server is already running!");
            return;
        }

        try {
            MyMod.LOG.info("[WebServer] Starting HTTP server on port {}...", port);

            // Initialize default routes if not already done
            if (RouteRegistry.getAllRoutes()
                .isEmpty()) {
                RouteRegistry.initializeDefaultRoutes();
            }

            // Create HTTP server
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // Register all routes
            registerRoutes();

            // Set thread pool executor
            server.setExecutor(Executors.newFixedThreadPool(nThreads));

            // Start server
            server.start();
            isRunning = true;

            MyMod.LOG.info("[WebServer] Access at: http://localhost:{}/", port);

        } catch (IOException e) {
            MyMod.LOG.error("[WebServer] ✗ Failed to start HTTP Server on port {}: {}", port, e.getMessage());
            MyMod.LOG.trace(e);
        }
    }

    /**
     * Stop the HTTP server
     */
    public static void stop() {
        if (server != null && isRunning) {
            MyMod.LOG.info("[WebServer] Stopping HTTP server...");
            server.stop(0);
            isRunning = false;
        }
    }

    /**
     * Check if the server is running
     */
    public static boolean isRunning() {
        return isRunning;
    }

    /**
     * Get the HTTP server instance
     */
    public static HttpServer getServer() {
        return server;
    }

    /**
     * Register all routes from RouteRegistry
     */
    private static void registerRoutes() {
        int routeCount = RouteRegistry.getAllRoutes()
            .size();
        MyMod.LOG.debug("[WebServer] Registering {} routes...", routeCount);

        // Register each route handler
        RouteRegistry.getAllRoutes()
            .forEach((path, handler) -> {
                server.createContext(path, handler);
                MyMod.LOG.debug(
                    "[WebServer]   Registered: {} -> {}",
                    path,
                    handler.getClass()
                        .getSimpleName());
            });

        MyMod.LOG.debug("[WebServer] ✓ All {} routes registered", routeCount);
    }

    /**
     * Add a new route handler dynamically
     */
    public static void addRoute(RouteHandler handler) {
        String path = handler.getPath();
        MyMod.LOG.info("[WebServer] Adding route '{}'...", path);

        RouteRegistry.register(handler);

        if (isRunning && server != null) {
            server.createContext(path, handler);
            MyMod.LOG.info(
                "[WebServer] ✓ Dynamically registered route: {} -> {}",
                path,
                handler.getClass()
                    .getSimpleName());
        }
    }
}
