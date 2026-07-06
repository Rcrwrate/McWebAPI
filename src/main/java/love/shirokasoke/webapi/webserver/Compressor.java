package love.shirokasoke.webapi.webserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;

/**
 * 自适应 HTTP 内容压缩协商器，仅响应体积超过 {@link #THRESHOLD} 时才协商压缩
 */
public class Compressor {

    /** 低于此字节数不压缩（压缩头开销大于收益） */
    public static final int THRESHOLD = 1024;

    /**
     * Accept-Encoding 按 RFC 7231 解析 q 值，选择 q 最高的可用算法。
     */
    public static String selectEncoding(HttpExchange exchange, int len) {
        if (len < THRESHOLD) return null;
        List<String> headers = exchange.getRequestHeaders()
            .get("Accept-Encoding");
        if (headers == null || headers.isEmpty()) return null;

        double bestQ = 0;
        String best = null;
        for (String h : headers) {
            if (h == null) continue;
            for (String tok : h.split(",")) {
                String[] parts = tok.trim()
                    .split(";");
                String name = parts[0].trim()
                    .toLowerCase();
                if (name.isEmpty()) continue;
                double q = 1.0;
                for (int i = 1; i < parts.length; i++) {
                    String p = parts[i].trim();
                    if (p.startsWith("q=")) {
                        try {
                            q = Double.parseDouble(
                                p.substring(2)
                                    .trim());
                        } catch (NumberFormatException ignore) {}
                    }
                }
                if (q <= 0) continue;
                String enc = matchSupported(name);
                if (enc != null && q > bestQ) {
                    bestQ = q;
                    best = enc;
                }
            }
        }
        return best;
    }

    private static String matchSupported(String name) {
        switch (name) {
            case "gzip":
            case "x-gzip":
                return "gzip";
            case "br":
                return Brotli.isAvailable() ? "br" : null;
            default:
                return null;
        }
    }

    public static byte[] compress(String encoding, byte[] data) throws IOException {
        if ("gzip".equals(encoding)) return gzip(data);
        if ("br".equals(encoding)) return Brotli.compress(data);
        return data;
    }

    public static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length / 4);
        try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            gz.write(data);
        }
        return baos.toByteArray();
    }

    /**
     * Brotli 编码器：反射调用 brotli4j。 缺失/加载失败/原生库不可用时自动禁用，
     * 后续不再尝试，{@link #selectEncoding} 将不再返回 "br"，从而回退 gzip。
     * <p>
     * 启用方式：将 brotli4j（含对应平台原生包）放入服务器 classpath 即可，无需改代码。
     */
    static final class Brotli {

        // 0=未探测, 1=可用, 2=禁用
        private static volatile int state = 0;
        private static java.lang.reflect.Method compressMethod;
        private static Object encoder;

        static boolean isAvailable() {
            int s = state;
            if (s == 1) return true;
            if (s == 2) return false;
            return init();
        }

        private static synchronized boolean init() {
            if (state != 0) return state == 1;
            try {
                Class<?> loader = Class.forName("com.aayushatharva.brotli4j.Brotli4jLoader");
                loader.getMethod("ensureAvailable")
                    .invoke(null);
                Class<?> encClass = Class.forName("com.aayushatharva.brotli4j.encoder.Encoder");
                encoder = encClass.getConstructor()
                    .newInstance();
                compressMethod = encClass.getMethod("compress", byte[].class);
                state = 1;
                MyMod.LOG.info("Brotli 编码器已加载（brotli4j），大型 JSON 将优先使用 br 压缩");
                return true;
            } catch (Throwable t) {
                state = 2;
                MyMod.LOG.debug("Brotli 不可用，大型响应将使用 gzip：{}", t.toString());
                return false;
            }
        }

        static byte[] compress(byte[] data) throws IOException {
            if (!isAvailable()) throw new IOException("brotli unavailable");
            try {
                return (byte[]) compressMethod.invoke(encoder, (Object) data);
            } catch (Throwable t) {
                // 运行期失败，禁用以避免后续请求重复尝试
                state = 2;
                throw new IOException("brotli compress failed", t);
            }
        }
    }
}
