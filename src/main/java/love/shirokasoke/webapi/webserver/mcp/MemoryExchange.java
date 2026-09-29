package love.shirokasoke.webapi.webserver.mcp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

/**
 * 内存版 {@link HttpExchange}
 * 
 * MCP 工具调用不经过真实 TCP，而是把 JSON-RPC 请求伪装成一次 HTTP
 */
public class MemoryExchange extends HttpExchange {

    private final String method;
    private final URI uri;
    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final InputStream requestBody;
    private final ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream(512);

    /**
     * 响应体输出流
     * 
     * @apiNote 仅允许取一次，避免 handler 里重复 getResponseBody() 造成混乱
     */
    private OutputStream responseBodyStream;

    private int statusCode = -1;
    private long responseLength = -1;
    private boolean closed = false;
    private final Map<String, Object> attributes = new HashMap<>();

    public MemoryExchange(String method, URI uri, byte[] requestBody, Map<String, String> headers) {
        this.method = method;
        this.uri = uri;
        this.requestBody = new ByteArrayInputStream(requestBody == null ? new byte[0] : requestBody);
        if (headers != null) {
            headers.forEach((k, v) -> requestHeaders.add(k, v));
        }
    }

    /** 便捷构造：GET 请求，无 body */
    public static MemoryExchange get(String path) {
        return new MemoryExchange("GET", URI.create(path), null, null);
    }

    /** 便捷构造：带 query 的请求 */
    public static MemoryExchange request(String method, String path, Map<String, String> query, byte[] body,
        Map<String, String> headers) {
        String qs = buildQueryString(query);
        URI uri = URI.create(qs.isEmpty() ? path : path + "?" + qs);
        return new MemoryExchange(method, uri, body, headers);
    }

    private static String buildQueryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            // 不在此处做 percent-encode：handler 用 getRawQuery() + URLDecoder 解析，
            // 若参数本身是 base64（含 '+'、'='），预先 encode 反而会被二次解码破坏。
            // 这里只保证结构合法，值中的 '&'、'=' 由调用方负责。
            sb.append(e.getKey())
                .append('=')
                .append(e.getValue() == null ? "" : e.getValue());
        }
        return sb.toString();
    }

    // region request

    @Override
    public Headers getRequestHeaders() {
        return requestHeaders;
    }

    @Override
    public Headers getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public URI getRequestURI() {
        return uri;
    }

    @Override
    public String getRequestMethod() {
        return method;
    }

    @Override
    public HttpContext getHttpContext() {
        return null;
    }

    @Override
    public void close() {
        // 幂等：RouteHandler.sendResponse 末尾会调用 exchange.close()
        closed = true;
    }

    @Override
    public InputStream getRequestBody() {
        return requestBody;
    }

    @Override
    public OutputStream getResponseBody() {
        if (responseBodyStream == null) {
            responseBodyStream = responseBuffer;
        }
        return responseBodyStream;
    }

    // region response

    @Override
    public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
        this.statusCode = rCode;
        this.responseLength = responseLength;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return new InetSocketAddress("127.0.0.1", 0);
    }

    @Override
    public int getResponseCode() {
        return statusCode;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return new InetSocketAddress("127.0.0.1", 0);
    }

    @Override
    public String getProtocol() {
        return "HTTP/1.1";
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public void setStreams(InputStream i, OutputStream o) {
        // 内存模式不支持替换流；静默忽略以免破坏 handler 逻辑
    }

    @Override
    public HttpPrincipal getPrincipal() {
        return null;
    }

    // region 扩展

    /** 已写入的响应体字节（未 flush 也可读 ） */
    public byte[] getResponseBodyBytes() {
        return responseBuffer.toByteArray();
    }

    /** 响应状态码；handler 未调用 sendResponseHeaders 时为 -1 */
    public int getStatusCode() {
        return statusCode;
    }

    public long getDeclaredResponseLength() {
        return responseLength;
    }

    public boolean isClosed() {
        return closed;
    }

    public String getResponseHeader(String name) {
        List<String> values = responseHeaders.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /** 把响应体按 UTF-8 读成字符串 */
    public String getResponseBodyAsString() {
        return new String(getResponseBodyBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 批量设置请求头（供工具调用时注入 Authorization 等） */
    public MemoryExchange withHeader(String name, String value) {
        if (name != null && value != null) {
            requestHeaders.add(name, value);
        }
        return this;
    }

    /** 调试用：列出已设置的响应头 */
    @Deprecated
    public List<String> responseHeaderNames() {
        return new ArrayList<>(responseHeaders.keySet());
    }
}
