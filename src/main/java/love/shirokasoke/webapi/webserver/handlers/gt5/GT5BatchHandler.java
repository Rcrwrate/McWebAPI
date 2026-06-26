package love.shirokasoke.webapi.webserver.handlers.gt5;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.ClassUtils;
import love.shirokasoke.webapi.utils.GT5Utils;
import love.shirokasoke.webapi.utils.log;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class GT5BatchHandler implements RouteHandler {

    private static final ConcurrentHashMap<String, BatchJob> JOBS = new ConcurrentHashMap<>();
    private static final long JOB_TTL_MS = 30 * 60 * 1000L;
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    private static class BatchJob {

        final String id;
        final List<coordinates> coords;
        final int total;
        final long createTime;
        volatile long startTime;
        final AtomicInteger completedCount = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        volatile long finishTime;
        volatile int runCount = 0;
        final List<String> errors = new ArrayList<>();
        // 每次执行产生一份新结果
        volatile ArrayNode results = mapper.createArrayNode();

        BatchJob(String id, List<coordinates> coords) {
            this.id = id;
            this.coords = coords;
            this.total = coords.size();
            this.createTime = System.currentTimeMillis();
        }

        void addError(String error) {
            synchronized (errors) {
                if (errors.size() < 1000) {
                    errors.add(error);
                }
            }
        }

        /** 重置执行状态以便重新运行 */
        void resetForRerun() {
            completedCount.set(0);
            successCount.set(0);
            failCount.set(0);
            finishTime = 0;
            startTime = 0;
            runCount++;
            synchronized (errors) {
                errors.clear();
            }
            results = mapper.createArrayNode();
        }

        String getStatus() {
            int completed = completedCount.get();
            if (completed >= total) return "completed";
            if (completed > 0) return "running";
            return "pending";
        }
    }

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
            throw new Error(
                400,
                "Request body must contain a non-empty 'machines' array: [{\"x\":0,\"y\":0,\"z\":0,\"dim\":0}, ...]");
        }
        List<coordinates> coords = parseMachineCoords(body);
        String jobId = String.valueOf(ID_GENERATOR.incrementAndGet());
        BatchJob job = new BatchJob(jobId, coords);
        JOBS.put(jobId, job);

        submitTasks(job);

        ObjectNode data = mapper.createObjectNode();
        data.put("id", job.id);
        data.put("total", job.total);
        sendResponse(exchange, data);
        cleanupExpiredJobs();
    }

    /** PATCH: 重新执行已有任务 */
    private void PATCH(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        String id = params.get("id");
        if (id == null || id.isEmpty()) {
            throw new Error(400, "Missing required parameter: id");
        }

        BatchJob job = JOBS.get(id);
        if (job == null) {
            throw new Error(404, "Task not found: " + id);
        }

        // 如果任务仍在运行中，拒绝重入
        if (job.getStatus()
            .equals("running")) {
            throw new Error(409, "Task is still running, cannot re-execute now");
        }

        job.resetForRerun();
        submitTasks(job);

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
            throw new Error(400, "Missing required parameter: id");
        }

        BatchJob job = JOBS.get(id);
        if (job == null) {
            throw new Error(404, "Task not found: " + id);
        }

        ObjectNode data = mapper.createObjectNode();
        data.put("id", job.id);
        data.put("total", job.total);
        data.put("completed", job.completedCount.get());
        data.put("success", job.successCount.get());
        data.put("failed", job.failCount.get());
        data.put("status", job.getStatus());
        data.put("runCount", job.runCount);
        data.put("createTime", job.createTime);
        if (job.finishTime > 0) {
            data.put("finishTime", job.finishTime);
            data.put("durationMs", job.finishTime - job.startTime);
        }

        // 错误详情（最多 100 条）
        synchronized (job.errors) {
            if (!job.errors.isEmpty()) {
                ArrayNode errArr = data.putArray("errors");
                int limit = Math.min(job.errors.size(), 100);
                for (int i = 0; i < limit; i++) {
                    errArr.add(job.errors.get(i));
                }
                if (job.errors.size() > 100) {
                    data.put("errorsTruncated", job.errors.size() - 100);
                }
            }
        }

        if (job.completedCount.get() >= job.total) {
            data.set("machines", job.results);
        }

        sendResponse(exchange, data);
    }

    private void submitTasks(BatchJob job) throws Error {
        MinecraftServer server = getServer();
        job.startTime = System.currentTimeMillis();

        for (coordinates coord : job.coords) {
            final int x = coord.posX;
            final int y = coord.posY;
            final int z = coord.posZ;
            final int dim = coord.dimension;

            ServerThreadDispatcher.scheduleOnServerThread(() -> {
                try {
                    fetchMachine(server, job, x, y, z, dim);
                } catch (Exception e) {
                    job.failCount.incrementAndGet();
                    job.addError(log.e(e));
                } finally {
                    int completed = job.completedCount.incrementAndGet();
                    if (completed >= job.total) {
                        job.finishTime = System.currentTimeMillis();
                    }
                }
            });
        }
    }

    /** 获取单个机器的完整信息 */
    private void fetchMachine(MinecraftServer server, BatchJob job, int x, int y, int z, int dim) {
        WorldServer world = server.worldServerForDimension(dim);
        if (world == null) {
            job.failCount.incrementAndGet();
            job.addError("Invalid dimension: " + dim + " at [" + x + "," + y + "," + z + "]");
            return;
        }

        TileEntity te = world.getTileEntity(x, y, z);
        MetaTileEntity mte = GT5Utils.extractValidMTE(te);
        if (mte == null) {
            // 非 GT5 机器，计入失败
            job.failCount.incrementAndGet();
            job.addError("Not a valid GT5 machine at [" + x + "," + y + "," + z + ",dim=" + dim + "]");
            return;
        }

        job.successCount.incrementAndGet();

        IGregTechTileEntity igte = (IGregTechTileEntity) te;
        ObjectNode machine = mapper.createObjectNode();
        machine.put("x", x);
        machine.put("y", y);
        machine.put("z", z);
        machine.put("dimension", dim);

        GT5Utils.writeBasicMachineInfo(igte, mte, machine);
        GT5Utils.writeState(igte, machine.putObject("state"));
        GT5Utils.write(mte, machine);
        ClassUtils.getClassInfo(mte, machine);

        synchronized (job.results) {
            job.results.add(machine);
        }
    }

    private List<coordinates> parseMachineCoords(JsonNode machinesNode) throws Error {
        List<coordinates> coords = new ArrayList<>();
        for (JsonNode node : machinesNode) {
            if (!node.has("x") || !node.has("y") || !node.has("z")) {
                throw new Error(400, "Each machine entry must have x, y, z fields");
            }
            int x = node.get("x")
                .asInt();
            int y = node.get("y")
                .asInt();
            int z = node.get("z")
                .asInt();
            int dim = node.has("dim") ? node.get("dim")
                .asInt() : 0;
            coords.add(new coordinates(x, y, z, dim));
        }
        return coords;
    }

    private void cleanupExpiredJobs() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, BatchJob>> it = JOBS.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<String, BatchJob> entry = it.next();
            BatchJob job = entry.getValue();
            if (job.finishTime > 0 && (now - job.finishTime) > JOB_TTL_MS) {
                it.remove();
            }
        }
    }
}
