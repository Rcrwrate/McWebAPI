package love.shirokasoke.webapi.webserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import com.aayushatharva.brotli4j.encoder.Encoder;
import com.github.luben.zstd.Zstd;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.Logs;

/**
 * 自适应 HTTP 内容压缩协商器，仅响应体积超过 {@link #THRESHOLD} 时才协商压缩
 */
public class Compressor {

    /** 低于此字节数不压缩（压缩头开销大于收益） */
    public static final int THRESHOLD = Config.Compressor_THRESHOLD;
    public static volatile boolean br = false;
    public static volatile boolean zstd = false;

    /**
     * Accept-Encoding 按 RFC 7231 解析 q 值，选择 q 最高的可用算法。
     */
    public static String selectEncoding(HttpExchange exchange, int len) {
        if (!Config.Compressor || len < THRESHOLD) return null;
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
                if (name != null && q > bestQ) {
                    bestQ = q;
                    best = name;
                }
            }
        }
        return best;
    }

    public static String fastCheck(HttpExchange exchange, int len) {
        if (!Config.Compressor || len < THRESHOLD) return null;
        List<String> headers = exchange.getRequestHeaders()
            .get("Accept-Encoding");
        if (headers == null || headers.isEmpty()) return null;
        for (String h : headers) {
            if (h == null) continue;
            if (h.indexOf("zstd") != -1 && zstd) return "zstd";
            if (h.indexOf("gzip") != -1) return "gzip";
            if (h.indexOf("br") != -1 && br) return "br";
        }
        return null;
    }

    public static byte[] compress(String encoding, byte[] data) throws IOException {
        if ("gzip".equals(encoding)) return gzip(data);
        if ("br".equals(encoding)) return Encoder.compress(data);
        if ("zstd".equals(encoding)) return Zstd.compress(data);
        return data;
    }

    public static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length / 4);
        try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            gz.write(data);
        }
        return baos.toByteArray();
    }

    public static void checkLoader() {
        if (!Config.Compressor) return;
        try {
            com.aayushatharva.brotli4j.Brotli4jLoader.ensureAvailability();
            br = com.aayushatharva.brotli4j.Brotli4jLoader.isAvailable();
        } catch (Throwable e) {
            if (!(e instanceof java.lang.NoClassDefFoundError)) {
                Logs.e(e);
            }
        }
        try {
            com.github.luben.zstd.util.Native.load();
            zstd = com.github.luben.zstd.util.Native.isLoaded();
        } catch (Throwable e) {
            if (!(e instanceof java.lang.NoClassDefFoundError)) {
                Logs.e(e);
            }
        }
        MyMod.LOG.info("压缩算法可用性:\tbr {} zstd {}", br, zstd);
    }
}
