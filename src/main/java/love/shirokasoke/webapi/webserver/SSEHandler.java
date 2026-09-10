package love.shirokasoke.webapi.webserver;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.Logs;
import love.shirokasoke.webapi.webserver.Auth.Auth;

/**
 * SSE (Server-Sent Events) 路由模板。
 *
 * <p>
 * 实现此接口即可获得一个 SSE 推送端点：鉴权、CORS、响应头与连接生命周期均由模板处理，
 * 只需在 {@link #run(HttpExchange, SSEClient)} 中以阻塞方式推送事件，方法返回后连接自动关闭。
 *
 * <pre>
 * 
 * {
 *     &#64;code
 *     public class TpsSSEHandler implements SSEHandler {
 *
 *         &#64;Override
 *         public String getPath() {
 *             return "/tps/sse";
 *         }
 *
 *         &#64;Override
 *         public void run(HttpExchange exchange, SSEClient client) throws Exception {
 *             client.retry(3000); // 断线后浏览器自动重连间隔(ms)
 *             while (client.isOpen()) {
 *                 client.eventJson("tps", buildTpsJson());
 *                 Thread.sleep(1000);
 *             }
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>
 * 注意：
 * <ul>
 * <li>客户端断开（写失败）后 {@link SSEClient#isOpen()} 变为 false，推送循环应据此退出；</li>
 * <li>事件若由外部线程驱动，可在 run 内阻塞于队列：
 * {@code while (client.isOpen()) client.event("x", queue.take());}</li>
 * <li>长时间无事件时建议周期性调用 {@link SSEClient#heartbeat()} 保活。</li>
 * </ul>
 */
public interface SSEHandler extends RouteHandler {

    /** HttpExchange 属性键：当前连接的 {@link SSEClient}，可在 run 之外获取 */
    String SSE_CLIENT_ATTR = "shirokasoke.sse.client";

    /**
     * 推送事件的主体逻辑（阻塞式）。方法返回后连接将被关闭。
     *
     * @param client 已就绪的 SSE 写入器
     */
    void run(HttpExchange exchange, SSEClient client) throws Exception;

    /**
     * 兼容 {@link RouteHandler} 单参签名的桥接方法，请勿覆写；实现双参版本即可
     */
    @Override
    default void run(HttpExchange exchange) throws Exception {
        SSEClient client = exchange.getAttribute(SSE_CLIENT_ATTR) instanceof SSEClient c ? c : null;
        if (client == null) {
            client = new SSEClient(exchange);
            client.open();
        }
        run(exchange, client);
    }

    @Override
    default void handle(HttpExchange exchange) throws IOException {
        long startTime = System.nanoTime();
        String method = exchange.getRequestMethod();
        String uri = exchange.getRequestURI()
            .toString();
        InetSocketAddress addr = exchange.getRemoteAddress();
        String remote = addr != null ? addr.getAddress()
            .getHostAddress() : "?";

        setCorsHeaders(exchange);
        // 处理 OPTIONS 预检请求
        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        // 流未开始，鉴权失败仍可正常返回 JSON 错误
        if (!Auth.auth(
            uri,
            method,
            exchange.getRequestHeaders()
                .get("Authorization"))) {
            sendErrorResponse(exchange, 401, "not auth", null);
            return;
        }

        // 设置 SSE 头部
        exchange.getResponseHeaders()
            .set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders()
            .set("Cache-Control", "no-cache");
        exchange.getResponseHeaders()
            .set("X-Accel-Buffering", "no"); // 禁用反向代理缓冲

        SSEClient client = new SSEClient(exchange);
        exchange.setAttribute(SSE_CLIENT_ATTR, client);
        try {
            client.open();
            run(exchange, client);
            double duration = (System.nanoTime() - startTime) / 1_000_000.0;
            MyMod.LOG
                .info("[{}]\t{} - {} - SSE closed after {}ms", method, uri, remote, String.format("%.3f", duration));
        } catch (Throwable e) {
            // 响应头已发送，无法再返回 JSON 错误，只能尽力推送 error 事件后关闭
            double duration = (System.nanoTime() - startTime) / 1_000_000.0;
            String message = e instanceof ApiException e2 ? "[" + e2.code + "] " + e2.getMessage()
                : e.getMessage() != null ? e.getMessage()
                    : e.getClass()
                        .getSimpleName();
            client.quietEvent("error", message);
            MyMod.LOG.error(
                "[{}]\t{} - {} - SSE error after {}ms\t{}",
                method,
                uri,
                remote,
                String.format("%.3f", duration),
                message);
            if (!(e instanceof ApiException)) {
                Logs.e(e);
            }
        } finally {
            client.close();
        }
    }
}
