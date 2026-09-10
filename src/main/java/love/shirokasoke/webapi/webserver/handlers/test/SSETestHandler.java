package love.shirokasoke.webapi.webserver.handlers.test;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.webserver.SSEClient;
import love.shirokasoke.webapi.webserver.SSEHandler;

public class SSETestHandler implements SSEHandler {

    @Override
    public String getPath() {
        return "/test/sse";
    }

    @Override
    public void run(HttpExchange exchange, SSEClient client) throws Exception {
        for (int i = 1; i < 50 && client.isOpen(); i++) {
            Thread.sleep(500);
            client.send(String.valueOf(i));
        }
    }
}
