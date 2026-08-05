package love.shirokasoke.webapi.webserver.handlers;

import java.io.IOException;
import java.io.InputStream;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.webserver.RouteHandler;

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

    @Override
    public void run(HttpExchange exchange) throws IOException {
        // Try to load resource from classpath
        InputStream is = StaticFileHandler.class.getResourceAsStream(resourcePath);

        if (is == null) {
            throw new ApiException(404, "Resource not found");
        }

        InputStream resourceStream = is;
        byte[] data = resourceStream.readAllBytes();

        setCache(exchange, 86400);
        exchange.getResponseHeaders()
            .set("Content-Type", contentType);
        sendResponse(exchange, data);
    }
}
