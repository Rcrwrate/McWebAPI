package love.shirokasoke.webapi.webserver.handlers.gt5;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.GT5Utils;
import love.shirokasoke.webapi.utils.log;
import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * 异步扫描指定区块内所有 GT5 机器，按 (x,z) 拆分为 256 个子任务投入慢队列。
 */
public class GT5ChunkScanHandler implements RouteHandler {

    private static final ConcurrentHashMap<String, ScanJob> JOBS = new ConcurrentHashMap<>();
    private static final long JOB_TTL_MS = 30 * 60 * 1000L;

    private static class ScanJob {

        final String id;
        final int total;
        final int chunkX;
        final int chunkZ;
        final int dimension;
        final AtomicInteger completedCount = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        final long createTime;
        volatile long finishTime;
        final ArrayNode machines = mapper.createArrayNode();
        final List<String> errors = new ArrayList<>();

        ScanJob(String id, int total, int chunkX, int chunkZ, int dimension) {
            this.id = id;
            this.total = total;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.createTime = System.currentTimeMillis();
        }

        void addError(String error) {
            synchronized (errors) {
                if (errors.size() < 1000) {
                    errors.add(error);
                }
            }
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
        return "/gt5/scan";
    }

    @Override
    public String getDescription() {
        return "Async scan all GT5 machines in a chunk (split into 256 cell sub-tasks). POST with chunkX & chunkZ (or x & z), dim to submit; GET with id to query.";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        if ("POST".equalsIgnoreCase(method)) {
            handleSubmit(exchange);
        } else {
            handleQuery(exchange);
        }
    }

    private void handleSubmit(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);

        int chunkX, chunkZ;
        if (params.containsKey("chunkX") && params.containsKey("chunkZ")) {
            chunkX = Integer.parseInt(params.get("chunkX"));
            chunkZ = Integer.parseInt(params.get("chunkZ"));
        } else if (params.containsKey("x") && params.containsKey("z")) {
            chunkX = Integer.parseInt(params.get("x")) >> 4;
            chunkZ = Integer.parseInt(params.get("z")) >> 4;
        } else {
            throw new RouteHandler.Error(400, "Missing required parameters. Provide either chunkX & chunkZ, or x & z");
        }

        int dimension = 0;
        if (params.containsKey("dim") || params.containsKey("dimension")) {
            dimension = Integer.parseInt(params.getOrDefault("dim", params.get("dimension")));
        }
        final int dim = dimension;

        MinecraftServer server = getServer();
        WorldServer world = server.worldServerForDimension(dim);
        if (world == null) {
            throw new Error(404, "Invalid dimension: " + dim);
        }

        if (!world.theChunkProviderServer.chunkExists(chunkX, chunkZ)) {
            throw new Error(404, "Chunk not loaded: chunkX=" + chunkX + ", chunkZ=" + chunkZ + ", dim=" + dim);
        }

        final Chunk chunk = world.theChunkProviderServer.loadChunk(chunkX, chunkZ);
        if (chunk == null) {
            throw new RouteHandler.Error(404, "Chunk not found: chunkX=" + chunkX + ", chunkZ=" + chunkZ);
        }

        String jobId = dim + "_" + chunkX + "_" + chunkZ;
        ScanJob job = new ScanJob(jobId, 256, chunkX, chunkZ, dim);
        JOBS.put(jobId, job);

        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;

        // 将 16*16 个位置拆为 256 个子任务投入慢队列
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int colX = x;
                final int colZ = z;
                final int worldX = baseX + x;
                final int worldZ = baseZ + z;
                ServerThreadDispatcher.scheduleOnServerThread(() -> {
                    try {
                        scanCell(world, chunk, job, worldX, worldZ, colX, colZ);
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

        ObjectNode data = mapper.createObjectNode();
        data.put("id", job.id);
        data.put("total", job.total);
        sendResponse(exchange, data);
        cleanupExpiredJobs();
    }

    private void handleQuery(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        String id = params.get("id");
        if (id == null || id.isEmpty()) {
            throw new RouteHandler.Error(400, "Missing required parameter: id");
        }

        ScanJob job = JOBS.get(id);
        if (job == null) {
            throw new RouteHandler.Error(404, "Task not found: " + id);
        }

        ObjectNode data = mapper.createObjectNode();
        data.put("id", job.id);
        data.put("total", job.total);
        data.put("completed", job.completedCount.get());
        data.put("success", job.successCount.get());
        data.put("failed", job.failCount.get());
        data.put("status", job.getStatus());
        data.put("createTime", job.createTime);
        data.put("chunkX", job.chunkX);
        data.put("chunkZ", job.chunkZ);
        data.put("dimension", job.dimension);
        if (job.finishTime > 0) {
            data.put("finishTime", job.finishTime);
            data.put("durationMs", job.finishTime - job.createTime);
        }

        // 写入错误详情（最多返回 100 条）
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
            ObjectNode result = mapper.createObjectNode();
            result.put("chunkX", job.chunkX);
            result.put("chunkZ", job.chunkZ);
            result.put("dimension", job.dimension);
            result.put("totalMachines", job.successCount.get());
            result.set("machines", job.machines);
            data.set("result", result);
        }
        sendResponse(exchange, data);
    }

    /** 扫描区块中的一个 (x,z) 位置，从 y=0 遍历到高度上限 */
    private void scanCell(WorldServer world, Chunk chunk, ScanJob job, int worldX, int worldZ, int colX, int colZ) {
        int height = chunk.getHeightValue(colX, colZ);
        for (int y = 0; y <= height; y++) {
            TileEntity te = world.getTileEntity(worldX, y, worldZ);
            MetaTileEntity mte = GT5Utils.extractValidMTE(te);
            if (mte == null) continue;

            job.successCount.incrementAndGet();

            ObjectNode machine = mapper.createObjectNode();
            machine.put("x", worldX);
            machine.put("y", y);
            machine.put("z", worldZ);
            GT5Utils.writeBasicMachineInfo((IGregTechTileEntity) te, mte, machine);

            job.machines.add(machine);
        }
    }

    private void cleanupExpiredJobs() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ScanJob>> it = JOBS.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<String, ScanJob> entry = it.next();
            ScanJob job = entry.getValue();
            if (job.finishTime > 0 && (now - job.finishTime) > JOB_TTL_MS) {
                it.remove();
            }
        }
    }
}
