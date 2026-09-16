package love.shirokasoke.webapi.webserver.handlers.gt5;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.webserver.RouteHandler;
import love.shirokasoke.webapi.webserver.handlers.gt5.GT5Batch.BatchJob;

public class GT5BatchHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/gt5/batch";
    }

    @Override
    public String getDescription() {
        return "Batch query GT5 machine info. POST with JSON body {machines:[{x,y,z,dim},...]} to submit; GET with id to query; PATCH with id to re-execute.";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        switch (method.toUpperCase()) {
            case "POST":
                POST(exchange);
                break;
            case "PATCH":
                PATCH(exchange);
                break;
            default:
                GET(exchange);
                break;
        }
    }

    private void POST(HttpExchange exchange) throws Exception {
        JsonNode body = getBody(exchange);
        if (body == null || !body.isArray() || body.isEmpty()) {
            throw new ApiException(
                400,
                "Request body must contain a non-empty 'machines' array: [{\"x\":0,\"y\":0,\"z\":0,\"dim\":0}, ...]");
        }
        List<coordinates> coords = GT5Batch.parseMachineCoords(body);
        BatchJob job = new BatchJob(coords);
        GT5Batch.submitTasks(job);

        ObjectNode data = mapper.createObjectNode();
        data.put("id", job.id);
        data.put("total", job.total);
        sendResponse(exchange, data);
        GT5Batch.cleanupExpiredJobs();
    }

    /** PATCH: 重新执行已有任务 */
    private void PATCH(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        String id = params.get("id");
        if (id == null || id.isEmpty()) {
            throw new ApiException(400, "Missing required parameter: id");
        }

        BatchJob job = GT5Batch.JOBS.get(id);
        if (job == null) {
            throw new ApiException(404, "Task not found: " + id);
        }

        // 如果任务仍在运行中，拒绝重入
        if (job.getStatus()
            .equals("running")) {
            throw new ApiException(409, "Task is still running, cannot re-execute now");
        }

        job.resetForRerun();
        GT5Batch.submitTasks(job);

        ObjectNode data = mapper.createObjectNode();
        data.put("id", job.id);
        data.put("total", job.total);
        data.put("runCount", job.runCount);
        sendResponse(exchange, data);
    }

    private void GET(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        String id = params.get("id");
        if (id == null || id.isEmpty()) {
            throw new ApiException(400, "Missing required parameter: id");
        }

        BatchJob job = GT5Batch.JOBS.get(id);
        if (job == null) {
            throw new ApiException(404, "Task not found: " + id);
        }
        sendResponse(exchange, job.getObjectNode());
    }
}
