package love.shirokasoke.webapi.webserver.handlers.gt5;

import static love.shirokasoke.webapi.Constant.mapper;

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

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.ClassUtils;
import love.shirokasoke.webapi.utils.GT5Utils;
import love.shirokasoke.webapi.utils.Logs;
import love.shirokasoke.webapi.utils.McAccessor;
import love.shirokasoke.webapi.webserver.RouteHandler.ApiException;
import love.shirokasoke.webapi.webserver.RouteHandler.coordinates;

public final class GT5Batch {

    public static final ConcurrentHashMap<String, BatchJob> JOBS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<coordinates, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private static record CacheEntry(MetaTileEntity mte, IGregTechTileEntity igte) {}

    private static final long JOB_TTL_MS = 30 * 60 * 1000L;
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    public static class BatchJob {

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

        BatchJob(List<coordinates> coords) {
            this.id = String.valueOf(ID_GENERATOR.incrementAndGet());
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

        ObjectNode getObjectNode() {
            ObjectNode data = mapper.createObjectNode();
            data.put("id", id);
            data.put("total", total);
            data.put("completed", completedCount.get());
            data.put("success", successCount.get());
            data.put("failed", failCount.get());
            data.put("status", getStatus());
            data.put("runCount", runCount);
            data.put("createTime", createTime);
            if (finishTime > 0) {
                data.put("finishTime", finishTime);
                data.put("durationMs", finishTime - startTime);
            }

            // 错误详情（最多 100 条）
            synchronized (errors) {
                if (!errors.isEmpty()) {
                    ArrayNode errArr = data.putArray("errors");
                    int limit = Math.min(errors.size(), 100);
                    for (int i = 0; i < limit; i++) {
                        errArr.add(errors.get(i));
                    }
                    if (errors.size() > 100) {
                        data.put("errorsTruncated", errors.size() - 100);
                    }
                }
            }

            if (completedCount.get() >= total) {
                data.set("machines", results);
            }

            return data;
        }
    }

    // region tools

    public static List<coordinates> parseMachineCoords(JsonNode machinesNode) throws ApiException {
        List<coordinates> coords = new ArrayList<>();
        for (JsonNode node : machinesNode) {
            if (!node.has("x") || !node.has("y") || !node.has("z")) {
                throw new ApiException(400, "Each machine entry must have x, y, z fields");
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

    public static void cleanupExpiredJobs() {
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

    // region main

    public static void submitTasks(BatchJob job) throws ApiException {
        JOBS.put(job.id, job);
        MinecraftServer server = McAccessor.getServer();
        job.startTime = System.currentTimeMillis();

        for (coordinates coord : job.coords) {
            ServerThreadDispatcher.scheduleOnServerThread(() -> {
                try {
                    fetchMachine(server, job, coord);
                } catch (Exception e) {
                    job.failCount.incrementAndGet();
                    job.addError(Logs.e(e));
                } finally {
                    int completed = job.completedCount.incrementAndGet();
                    if (completed >= job.total) {
                        job.finishTime = System.currentTimeMillis();
                    }
                }
            });
        }
    }

    /**
     * 缓存条目是否仍存活。
     * <p>
     * {@link MetaTileEntity#isValid()} 内部校验 MTE 的 base 指针与 TE 的 dead 标志，
     * 覆盖拆机、invalidate、区块卸载三类失效场景；
     * 再比对一次指针，确保条目里的 MTE 与 TE 仍是同一台机器的组合。
     */
    private static boolean isAlive(CacheEntry entry) {
        MetaTileEntity mte = entry.mte();
        return mte.getBaseMetaTileEntity() == entry.igte() && mte.isValid();
    }

    /**
     * 获取单个机器的完整信息，
     * 
     * @apiNote 主线程运行，无需关心线程安全
     */
    public static void fetchMachine(MinecraftServer server, BatchJob job, coordinates coord) {
        CacheEntry data = CACHE.compute(coord, (key, entry) -> {
            if (entry != null && isAlive(entry)) return entry;
            // 缓存失效
            MyMod.LOG.debug("GT5 Cache miss {}", coord);
            WorldServer world = server.worldServerForDimension(coord.dimension);
            if (world == null) {
                return null;
            }
            TileEntity te = world.getTileEntity(coord.posX, coord.posY, coord.posZ);
            MetaTileEntity mte = GT5Utils.extractValidMTE(te);
            return mte == null ? null : new CacheEntry(mte, (IGregTechTileEntity) te);
        });

        if (data == null) {
            // 非 GT5 机器（或机器已失效且现场无法解析），计入失败
            job.failCount.incrementAndGet();
            job.addError(
                "Not a valid GT5 machine at [" + coord.posX
                    + ","
                    + coord.posY
                    + ","
                    + coord.posZ
                    + ",dim="
                    + coord.dimension
                    + "]");
            return;
        }
        final MetaTileEntity mte = data.mte();
        final IGregTechTileEntity igte = data.igte();

        job.successCount.incrementAndGet();

        ObjectNode machine = mapper.createObjectNode();
        machine.put("x", coord.posX);
        machine.put("y", coord.posY);
        machine.put("z", coord.posZ);
        machine.put("dimension", coord.dimension);

        GT5Utils.writeBasicMachineInfo(igte, mte, machine);
        GT5Utils.writeState(igte, machine.putObject("state"));
        GT5Utils.write(mte, machine);
        ClassUtils.getClassInfo(mte, machine);

        synchronized (job.results) {
            job.results.add(machine);
        }
    }
}
