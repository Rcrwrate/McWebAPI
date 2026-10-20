package love.shirokasoke.webapi.webserver;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.sun.net.httpserver.HttpServer;

import love.shirokasoke.webapi.MyMod;

/**
 * WebServer - HTTP Server for WebAPI
 * Manages HTTP server lifecycle and route registration
 */
public class WebServer {

    private static volatile HttpServer server;
    private static volatile boolean isRunning = false;

    public static void start(int port, int nThreads, boolean useVirtualThreads) {
        if (isRunning) {
            MyMod.LOG.warn("[WebServer] Server is already running!");
            return;
        }

        try {
            MyMod.LOG.info("[WebServer] Starting HTTP server on port {}...", port);

            if (RouteRegistry.getAllRoutes()
                .isEmpty()) {
                RouteRegistry.initializeDefaultRoutes();
            }

            server = HttpServer.create(new InetSocketAddress(port), 0);
            registerRoutes();
            if (useVirtualThreads) {
                MyMod.LOG.info("[WebServer] Using virtual threads executor");
                final ThreadFactory virtualThreadFactory = Thread.ofVirtual()
                    .name("http-virtual-", 1)
                    .factory();
                server.setExecutor(Executors.newThreadPerTaskExecutor(virtualThreadFactory));
            } else {
                MyMod.LOG.info("[WebServer] Using fixed thread pool with {} threads", nThreads);
                final ThreadFactory threadFactory = Thread.ofPlatform()
                    .name("http-workers-", 1)
                    .daemon(false)
                    .factory();
                server.setExecutor(Executors.newFixedThreadPool(nThreads, threadFactory));
            }

            try {
                startDispatcherReflectively();
                MyMod.LOG.info("[WebServer] HTTP-Dispatcher started via reflection (inheritThreadLocals=true)");
            } catch (Exception e) {
                MyMod.LOG.error(
                    "[WebServer] Failed to start HTTP-Dispatcher via reflection, fallback to server.start(): {}",
                    e.getMessage());
                MyMod.LOG.trace(e);
                server.start();
            }
            isRunning = true;

            MyMod.LOG.info("[WebServer] Access at: http://localhost:{}/", port);

        } catch (IOException e) {
            MyMod.LOG.error("[WebServer] ✗ Failed to start HTTP Server on port {}: {}", port, e.getMessage());
            MyMod.LOG.trace(e);
        }
        Compressor.checkLoader();
    }

    /**
     * HTTP-Dispatcher 线程创建时默认不携带上下文 {@link InheritableThreadLocal}
     * <p>
     * 以反射方式完全替换原本的启动方式，以修复Web线程上下文丢失
     */
    private static void startDispatcherReflectively() throws ReflectiveOperationException {
        final Class<?> unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
        final Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        final Object unsafe = theUnsafe.get(null);

        final Method objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", Field.class);
        // JDK 24+ 将 getObject/putObject 更名为 getReference/putReference，取第一个可用的
        final Method getReference = findUnsafeMethod(
            unsafeClass,
            List.of("getReference", "getObject"),
            Object.class,
            Long.TYPE);
        final Method putReference = findUnsafeMethod(
            unsafeClass,
            List.of("putReference", "putObject"),
            Object.class,
            Long.TYPE,
            Object.class);
        final Method putBoolean = unsafeClass.getMethod("putBoolean", Object.class, Long.TYPE, Boolean.TYPE);
        objectFieldOffset.setAccessible(true);
        getReference.setAccessible(true);
        putReference.setAccessible(true);
        putBoolean.setAccessible(true);

        // HttpServer.create() 实际返回 sun.net.httpserver.HttpServerImpl，其 server 字段才是 ServerImpl
        final Field implField = server.getClass()
            .getDeclaredField("server");
        final long implOffset = (Long) objectFieldOffset.invoke(unsafe, implField);
        final Object serverImpl = getReference.invoke(unsafe, server, implOffset);
        final Class<?> serverImplClass = serverImpl.getClass();

        // ServerImpl.dispatcher
        final Field dispatcherField = serverImplClass.getDeclaredField("dispatcher");
        final long dispatcherOffset = (Long) objectFieldOffset.invoke(unsafe, dispatcherField);
        final Runnable dispatcher = (Runnable) getReference.invoke(unsafe, serverImpl, dispatcherOffset);

        // 等价于 new Thread(null, dispatcher, "HTTP-Dispatcher", 0, true)，第 5 个参数 false -> true
        final Thread dispatcherThread = Thread.ofPlatform()
            .name("HTTP-Dispatcher")
            .inheritInheritableThreadLocals(true)
            .unstarted(dispatcher);

        // 写回 ServerImpl 内部状态，保持与 ServerImpl.start() 一致
        // stop() 依赖 dispatcherThread 执行 join，Exchange.run() 依赖它判断当前是否 dispatcher 线程
        final Field dispatcherThreadField = serverImplClass.getDeclaredField("dispatcherThread");
        final long dispatcherThreadOffset = (Long) objectFieldOffset.invoke(unsafe, dispatcherThreadField);
        putReference.invoke(unsafe, serverImpl, dispatcherThreadOffset, dispatcherThread);

        final Field startedField = serverImplClass.getDeclaredField("started");
        final long startedOffset = (Long) objectFieldOffset.invoke(unsafe, startedField);
        putBoolean.invoke(unsafe, serverImpl, startedOffset, Boolean.TRUE);

        dispatcherThread.start();
    }

    private static Method findUnsafeMethod(Class<?> unsafeClass, List<String> names, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        for (String name : names) {
            try {
                return unsafeClass.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException("jdk.internal.misc.Unsafe has none of " + names);
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

    public static void removeRoute(String path) {
        try {
            MyMod.LOG.info("[WebServer] Removing route '{}'...", path);
            server.removeContext(path);
        } catch (Exception e) {
            MyMod.LOG.error("[WebServer] Error removing route '{}': {}", path, e.getMessage());
        }
    }
}
