package love.shirokasoke.webapi.webserver.handlers.block;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.Logs;
import love.shirokasoke.webapi.utils.McAccessor;

public class BatchSetBlockHandler extends BlockHandler {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);
    private static final ConcurrentHashMap<String, BatchJob> JOBS = new ConcurrentHashMap<>();
    private static final long JOB_TTL_MS = 30 * 60 * 1000L; // 已完成任务保留 30 分钟

    @Override
    public String getPath() {
        return "/batchsetblock";
    }

    @Override
    public String getDescription() {
        return "Batch setblock via task queue. POST to submit, GET?id= to query result.";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("POST".equals(method)) {
            handleSubmit(exchange);
        } else if ("GET".equals(method)) {
            handleQuery(exchange);
        } else {
            throw new ApiException(400, "Method must be POST or GET");
        }
    }

    private void handleSubmit(HttpExchange exchange) throws IOException {
        JsonNode root = getBody(exchange);
        if (!root.isArray()) {
            throw new ApiException(400, "Request body must be a JSON array");
        }

        int size = root.size();
        if (size == 0) {
            throw new ApiException(400, "Task array is empty");
        }
        if (size > 65536) {
            throw new ApiException(400, "Too many tasks (max 65536)");
        }

        // 预校验所有任务并收集
        MinecraftServer server = McAccessor.getServer();
        List<SetBlockTask> batchTasks = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            JsonNode taskNode = root.get(i);

            int x = taskNode.path("x")
                .asInt();
            int y = taskNode.path("y")
                .asInt();
            int z = taskNode.path("z")
                .asInt();
            int dim = taskNode.path("dim")
                .asInt(0);
            int id = taskNode.path("id")
                .asInt();
            int metadata = taskNode.path("metadata")
                .asInt(0);
            int flag = taskNode.path("flag")
                .asInt(2);

            WorldServer world = McAccessor.getWorld(server, dim);
            if (world == null) {
                throw new ApiException(400, "Invalid dimension " + dim + " at tasks[" + i + "]");
            }

            Block block = Block.getBlockById(id);
            if (block == null) {
                throw new ApiException(400, "Block id not found at tasks[" + i + "]");
            }

            batchTasks.add(new SetBlockTask(world, x, y, z, block, metadata, flag));
        }

        String jobId = String.valueOf(ID_GENERATOR.incrementAndGet());
        BatchJob job = new BatchJob(jobId, size);
        JOBS.put(jobId, job);

        // 将所有子任务投入慢队列
        for (SetBlockTask task : batchTasks) {
            ServerThreadDispatcher.scheduleOnServerThread(() -> {
                try {
                    boolean changed = task.world.setBlock(task.x, task.y, task.z, task.block, task.metadata, task.flag);
                    if (changed) {
                        job.successCount.incrementAndGet();
                    } else {
                        job.failCount.incrementAndGet();
                        job.addFailure(task.x, task.y, task.z, "setBlock returned false");
                    }
                } catch (Exception e) {
                    job.failCount.incrementAndGet();
                    job.addFailure(task.x, task.y, task.z, Logs.e(e));
                } finally {
                    int completed = job.completedCount.incrementAndGet();
                    if (completed >= job.total) {
                        job.finishTime = System.currentTimeMillis();
                    }
                }
            });
        }

        ObjectNode rep = mapper.createObjectNode()
            .put("id", jobId)
            .put("total", size);
        sendResponse(exchange, rep);
        cleanupExpiredJobs();
    }

    private void handleQuery(HttpExchange exchange) throws IOException {
        String id = parseQueryParams(exchange).get("id");
        if (id == null || id.isEmpty()) {
            throw new ApiException(400, "Missing query param 'id'");
        }

        BatchJob job = JOBS.get(id);
        if (job == null) {
            throw new ApiException(404, "Job not found: " + id);
        }

        sendResponse(exchange, buildJobDetail(job));
    }

    private ObjectNode buildJobDetail(BatchJob job) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", job.id);
        node.put("total", job.total);
        node.put("completed", job.completedCount.get());
        node.put("success", job.successCount.get());
        node.put("failed", job.failCount.get());
        node.put("status", getJobStatus(job));
        node.put("createTime", job.createTime);
        if (job.finishTime > 0) {
            node.put("finishTime", job.finishTime);
            node.put("durationMs", job.finishTime - job.createTime);
        }
        // 写入失败详情（最多返回 100 条）
        synchronized (job.failures) {
            if (!job.failures.isEmpty()) {
                ArrayNode failArr = node.putArray("failures");
                int limit = Math.min(job.failures.size(), 100);
                for (int i = 0; i < limit; i++) {
                    FailureDetail f = job.failures.get(i);
                    ObjectNode fNode = failArr.addObject();
                    fNode.put("x", f.x);
                    fNode.put("y", f.y);
                    fNode.put("z", f.z);
                    fNode.put("reason", f.reason);
                }
                if (job.failures.size() > 100) {
                    node.put("failuresTruncated", job.failures.size() - 100);
                }
            }
        }
        return node;
    }

    private String getJobStatus(BatchJob job) {
        int completed = job.completedCount.get();
        if (completed >= job.total) {
            return "completed";
        } else if (completed > 0) {
            return "running";
        } else {
            return "pending";
        }
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

    private static class BatchJob {

        final String id;
        final int total;
        final AtomicInteger completedCount = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        final long createTime;
        volatile long finishTime;
        final List<FailureDetail> failures = new ArrayList<>();

        BatchJob(String id, int total) {
            this.id = id;
            this.total = total;
            this.createTime = System.currentTimeMillis();
        }

        void addFailure(int x, int y, int z, String reason) {
            synchronized (failures) {
                if (failures.size() < 1000) { // 最多记录 1000 条失败详情
                    failures.add(new FailureDetail(x, y, z, reason));
                }
            }
        }
    }

    private static class FailureDetail {

        final int x, y, z;
        final String reason;

        FailureDetail(int x, int y, int z, String reason) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.reason = reason;
        }
    }

    private static class SetBlockTask {

        final WorldServer world;
        final int x, y, z;
        final Block block;
        final int metadata;
        final int flag;

        SetBlockTask(WorldServer world, int x, int y, int z, Block block, int metadata, int flag) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.metadata = metadata;
            this.flag = flag;
        }
    }
}
