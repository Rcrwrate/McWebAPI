package love.shirokasoke.webapi.server.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.RouteHandler;

public class StaticFileHandler implements RouteHandler {

    private final String resourcePath;
    private final String contentType;
    private final String path;

    public StaticFileHandler(String resourcePath, String contentType, String path) {
        this.resourcePath = resourcePath;
        this.contentType = contentType;
        this.path = path;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getDescription() {
        return "Serve static files like favicon.ico";
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] temp = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(temp)) != -1) {
            buffer.write(temp, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        // Try to load resource from classpath
        InputStream is = getClass().getClassLoader()
            .getResourceAsStream(resourcePath);

        if (is == null) {
            // Resource not found
            sendErrorResponse(exchange, 404, "Resource not found");
            return;
        }

        try (InputStream resourceStream = is) {
            // Read all bytes from resource
            byte[] data = resourceStream.readAllBytes();

            // Set content type and length
            exchange.getResponseHeaders()
                .set("Content-Type", contentType);
            exchange.getResponseHeaders()
                .set("Cache-Control", "max-age=86400"); // Cache for 24 hours
            exchange.sendResponseHeaders(200, data.length);

            // Write response
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }

            MyMod.LOG.debug("[StaticFileHandler] Served: {}", resourcePath);
        } catch (Exception e) {
            MyMod.LOG.error("[StaticFileHandler] Error serving resource: {}", e.getMessage());
            sendErrorResponse(exchange, 500, "Internal server error");
        }
    }
}
