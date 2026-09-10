package love.shirokasoke.webapi.webserver;

import static java.nio.charset.StandardCharsets.UTF_8;
import static love.shirokasoke.webapi.Constant.mapper;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;

public class SSEClient implements Closeable {

    private final HttpExchange exchange;
    private OutputStream out;
    private volatile boolean open = false;

    SSEClient(HttpExchange exchange) {
        this.exchange = exchange;
    }

    /** 发送响应头并进入流模式（模板内部调用） */
    public void open() throws IOException {
        exchange.sendResponseHeaders(200, 0); // 0 = 长度未知，chunked 传输
        this.out = exchange.getResponseBody();
        this.open = true;
    }

    /** 连接是否仍然可用（客户端断开时为 false） */
    public boolean isOpen() {
        return open;
    }

    /** 推送注释心跳（{@code : ping}），用于保活、防止代理超时 */
    public boolean heartbeat() {
        return writeFrame(": ping\n\n");
    }

    /** 设置断线重连间隔（毫秒），对应 SSE {@code retry:} 字段 */
    public boolean retry(long millis) {
        return writeFrame("retry: " + millis + "\n\n");
    }

    /** 推送无名事件（客户端按默认 message 类型接收），data 支持多行 */
    public boolean send(String data) {
        return event(null, data, null);
    }

    /** 推送无名事件（客户端按默认 message 类型接收），data 为已编码的 UTF-8 文本字节（如 JSON） */
    public boolean send(byte[] data) {
        return event(null, data, null);
    }

    /** 推送命名事件，data 支持多行（每行自动补 {@code data: } 前缀） */
    public boolean event(String event, String data) {
        return event(event, data, null);
    }

    /** 推送命名事件，data 为已编码的 UTF-8 文本字节（如 JSON），直接解码发送，零序列化开销 */
    public boolean event(String event, byte[] data) {
        return event(event, data, null);
    }

    /** 推送命名事件，data 序列化为 JSON */
    public boolean eventJson(String event, Object data) throws IOException {
        return event(event, mapper.writeValueAsString(data), null);
    }

    /** 推送命名事件（data 为已编码的 UTF-8 文本字节），可携带事件 id */
    public boolean event(String event, byte[] data, String id) {
        StringBuilder sb = new StringBuilder();
        if (event != null && !event.isEmpty()) {
            sb.append("event: ")
                .append(stripNewlines(event))
                .append('\n');
        }
        if (id != null && !id.isEmpty()) {
            sb.append("id: ")
                .append(stripNewlines(id))
                .append('\n');
        }
        sb.append("data: ");

        byte[] head = sb.toString()
            .getBytes(UTF_8);
        byte[] end = new StringBuilder().append('\n')
            .append('\n')
            .toString()
            .getBytes();

        byte[] result = new byte[head.length + data.length + end.length];

        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(data, 0, result, head.length, data.length);
        System.arraycopy(end, 0, result, head.length + data.length, end.length);
        return writeFrame(result);

    }

    /** 推送命名事件，可携带事件 id（供客户端断线续传 Last-Event-ID 使用） */
    public boolean event(String event, String data, String id) {
        StringBuilder sb = new StringBuilder();
        if (event != null && !event.isEmpty()) {
            sb.append("event: ")
                .append(stripNewlines(event))
                .append('\n');
        }
        if (id != null && !id.isEmpty()) {
            sb.append("id: ")
                .append(stripNewlines(id))
                .append('\n');
        }
        for (String line : data.split("\r\n|\r|\n", -1)) {
            sb.append("data: ")
                .append(line)
                .append('\n');
        }
        sb.append('\n');
        return writeFrame(sb.toString());
    }

    /** 同 {@link #event(String, String)}，但吞掉所有异常，用于错误收尾 */
    void quietEvent(String event, String data) {
        try {
            event(event, data);
        } catch (Throwable ignored) {}
    }

    private boolean writeFrame(String frame) {
        return writeFrame(frame.getBytes(UTF_8));
    }

    private synchronized boolean writeFrame(byte[] frame) {
        if (!open || out == null) return false;
        try {
            out.write(frame);
            out.flush();
            return true;
        } catch (IOException e) {
            // 客户端已断开
            open = false;
            return false;
        }
    }

    private static String stripNewlines(String s) {
        return s.replace('\r', ' ')
            .replace('\n', ' ');
    }

    /** 关闭连接，可重复调用 */
    @Override
    public void close() {
        open = false;
        try {
            exchange.close();
        } catch (Throwable ignored) {}
    }
}
