package love.shirokasoke.webapi.webserver.handlers.gt5;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.webserver.SSEClient;
import love.shirokasoke.webapi.webserver.SSEHandler;
import love.shirokasoke.webapi.webserver.handlers.gt5.GT5Batch.BatchJob;

public class GT5BatchSSEHandler implements SSEHandler {

    @Override
    public String getPath() {
        return "/gt5/batch/sse";
    }

    @Override
    public String getDescription() {
        return "Batch query GT5 machine info. POST with JSON body {machines:[{x,y,z,dim},...]} to submit; GET with id to query; PATCH with id to re-execute.";
    }

    @Override
    public void run(HttpExchange exchange, SSEClient client) throws Exception {
        if (!exchange.getRequestMethod()
            .equals("POST")) {
            throw new ApiException(405, "method must be POST");
        }
        final Map<String, String> params = parseQueryParams(exchange);
        final int sleep = 1000 * Integer.valueOf(params.getOrDefault("interval", "5"));

        JsonNode body = getBody(exchange);
        if (body == null || !body.isArray() || body.isEmpty()) {
            throw new ApiException(
                400,
                "Request body must contain a non-empty 'machines' array: [{\"x\":0,\"y\":0,\"z\":0,\"dim\":0}, ...]");
        }
        final BatchJob job = new BatchJob(GT5Batch.parseMachineCoords(body));
        GT5Batch.submitTasks(job);
        client.retry(3000);

        boolean isSended = false;
        long lastSubmit = System.currentTimeMillis();
        while (client.isOpen()) {
            long check = System.currentTimeMillis() - lastSubmit;
            if (job.getStatus()
                .equals("completed")) {
                if (!isSended) {
                    isSended = true;
                    client.eventJson("gt5", job.getObjectNode());
                }
                if (check > sleep) {
                    lastSubmit = System.currentTimeMillis();
                    isSended = false;
                    job.resetForRerun();
                    GT5Batch.submitTasks(job);
                }
            }
            client.heartbeat();
            Thread.sleep(1000);
        }
        GT5Batch.cleanupExpiredJobs();
    }
}
