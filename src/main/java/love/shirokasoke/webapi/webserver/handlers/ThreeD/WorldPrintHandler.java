package love.shirokasoke.webapi.webserver.handlers.ThreeD;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import li.cil.oc.common.tileentity.Print;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.Logs;
import love.shirokasoke.webapi.utils.McAccessor;
import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * 请求体为一张图片，转换为 OC 3D 打印件后在世界中铺设像素画。
 * (x,y,z) 为图片左上角小块（i=0, j=0）的位置，图片向右（观察者视角）和向下延展；
 * facing 为画面朝向（north/south/east/west，即观察者所在的一侧）。
 * 每个方块一个子任务投递到慢队列执行；GET ?id= 查询任务进度。
 */
public class WorldPrintHandler implements RouteHandler {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);
    private static final ConcurrentHashMap<String, PrintJob> JOBS = new ConcurrentHashMap<>();
    private static final long JOB_TTL_MS = 30 * 60 * 1000L; // 已完成任务保留 30 分钟

    /** 请求体大小上限 32MB */
    private static final int MAX_BODY_BYTES = 32 * 1024 * 1024;

    /** 单次最大铺设方块数 */
    private static final int MAX_BLOCKS = 65536;

    @Override
    public String getPath() {
        return "/3d/world";
    }

    @Override
    public String getDescription() {
        return "PUT 上传图片 → 在世界中铺设 OC 3D 打印像素画（慢队列逐块执行）。Query 参数: x,y,z, dim, facing, 可选 label/tooltip；GET ?id= 查询任务";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        if ("PUT".equals(method)) {
            handleSubmit(exchange);
        } else if ("GET".equals(method)) {
            handleQuery(exchange);
        } else {
            throw new ApiException(400, "Method must be PUT or GET");
        }
    }

    private void handleSubmit(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        coordinates co = getCoordinates(params);
        int x = co.posX;
        int y = co.posY;
        int z = co.posZ;
        int dim = co.dimension;
        ForgeDirection facing = parseFacing(params.getOrDefault("facing", "south"));

        WorldServer world = McAccessor.getWorld(dim);

        byte[] imageData;
        try (InputStream is = exchange.getRequestBody()) {
            imageData = is.readAllBytes();
        }
        if (imageData.length == 0) {
            throw new ApiException(400, "Empty request body");
        }
        if (imageData.length > MAX_BODY_BYTES) {
            throw new ApiException(400, "Image too large: " + imageData.length + " bytes");
        }

        int[] checked = PrintUtils.checkImage(imageData);
        if (checked[1] > y * 16) {
            throw new ApiException(400, "Image's height is to large: " + checked[1]);
        }

        String label = params.getOrDefault("label", "3d-print %d,%d");
        String tooltip = params.getOrDefault("tooltip", "created by webapi");

        // 图片解码与形状/NBT 生成为纯 CPU 操作，在 HTTP 线程完成；慢队列任务只做 setBlock
        final BufferedImage img = PrintUtils.padToBlocks(PrintUtils.loadImage(imageData));

        int cols = img.getWidth() / 16;
        int rows = img.getHeight() / 16;
        if ((long) cols * rows > MAX_BLOCKS || rows > y) {
            throw new ApiException(400, "Too many blocks: " + cols + "x" + rows);
        }

        // 图片列在水平面内的延展方向（与 ExtendedAABB.rotateTowards(facing) 的块内旋转保持一致，
        // 保证观察者从 facing 一侧看到的画面不镜像）；行永远沿 -y 向下
        int colDx;
        int colDz;
        switch (facing) {
            case SOUTH:
                colDx = 1;
                colDz = 0; // rotateY(0)：列 → +x
                break;
            case NORTH:
                colDx = -1;
                colDz = 0; // rotateY(2)：列 → -x
                break;
            case EAST:
                colDx = 0;
                colDz = -1; // rotateY(1)：列 → -z
                break;
            case WEST:
                colDx = 0;
                colDz = 1; // rotateY(3)：列 → +z
                break;
            default:
                throw new ApiException(400, "Unsupported facing: " + facing);
        }

        final List<PlaceTask> tasks = new ArrayList<>(cols * rows);
        int skipped = 0;
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                ItemStack stack = PrintUtils.createPrint(
                    img,
                    i * 16,
                    j * 16,
                    String.format(label, i * 16, j * 16),
                    String.format(tooltip, i * 16, j * 16));
                if (stack == null) {
                    skipped++; // 全透明块：跳过但保持坐标对齐
                    continue;
                }
                tasks.add(
                    new PlaceTask(
                        world,
                        x + colDx * i,
                        y - j,
                        z + colDz * i,
                        Block.getBlockFromItem(stack.getItem()),
                        stack,
                        facing));
            }
        }

        if (tasks.isEmpty()) {
            throw new ApiException(400, "Image is fully transparent, nothing to place");
        }

        String jobId = String.valueOf(ID_GENERATOR.incrementAndGet());
        PrintJob job = new PrintJob(jobId, tasks.size());
        JOBS.put(jobId, job);

        // 每个方块一个小任务，投递到慢队列
        for (PlaceTask task : tasks) {
            ServerThreadDispatcher.scheduleOnServerThread(() -> placeOne(task, job));
        }

        ObjectNode rep = mapper.createObjectNode()
            .put("id", jobId)
            .put("total", job.total)
            .put("skipped", skipped);
        sendResponse(exchange, rep);
        cleanupExpiredJobs();
    }

    /** 放置单个打印件：setBlock → 灌入 PrintData → 设置朝向 → 同步客户端 */
    private static void placeOne(PlaceTask task, PrintJob job) {
        try {
            boolean changed = task.world.setBlock(task.x, task.y, task.z, task.block, 0, 3);
            if (!changed) {
                job.failCount.incrementAndGet();
                job.addFailure(task.x, task.y, task.z, "setBlock returned false");
                return;
            }
            TileEntity te = task.world.getTileEntity(task.x, task.y, task.z);
            if (te instanceof Print print) {
                // 与 placeBlockAt → doCustomInit 等价：NBT 从物品转移到 TileEntity
                print.data()
                    .load(task.stack);
                // setFromFacing 仅在朝向变化时触发 updateBounds，此处显式调用兜底
                print.setFromFacing(task.facing);
                print.updateBounds();
                task.world.markBlockForUpdate(task.x, task.y, task.z);
                job.successCount.incrementAndGet();
            } else {
                job.failCount.incrementAndGet();
                job.addFailure(task.x, task.y, task.z, "TileEntity is not an OC print");
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
    }

    private void handleQuery(HttpExchange exchange) throws IOException {
        String id = parseQueryParams(exchange).get("id");
        if (id == null || id.isEmpty()) {
            throw new ApiException(400, "Missing query param 'id'");
        }
        PrintJob job = JOBS.get(id);
        if (job == null) {
            throw new ApiException(404, "Job not found: " + id);
        }
        sendResponse(exchange, buildJobDetail(job));
    }

    private ObjectNode buildJobDetail(PrintJob job) {
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

    private String getJobStatus(PrintJob job) {
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
        Iterator<Map.Entry<String, PrintJob>> it = JOBS.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<String, PrintJob> entry = it.next();
            if (entry.getValue().finishTime > 0 && (now - entry.getValue().finishTime) > JOB_TTL_MS) {
                it.remove();
            }
        }
    }

    /** 打印件渲染仅做 Y 轴旋转（rotateTowards），只支持四个水平朝向 */
    private static ForgeDirection parseFacing(String value) throws ApiException {
        switch (value.toLowerCase()) {
            case "north":
                return ForgeDirection.NORTH;
            case "south":
                return ForgeDirection.SOUTH;
            case "east":
                return ForgeDirection.EAST;
            case "west":
                return ForgeDirection.WEST;
            default:
                throw new ApiException(400, "Invalid facing: " + value + " (north/south/east/west)");
        }
    }

    private static class PrintJob {

        final String id;
        final int total;
        final AtomicInteger completedCount = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        final long createTime;
        volatile long finishTime;
        final List<FailureDetail> failures = new ArrayList<>();

        PrintJob(String id, int total) {
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

    private static class PlaceTask {

        final WorldServer world;
        final int x, y, z;
        final Block block;
        final ItemStack stack;
        final ForgeDirection facing;

        PlaceTask(WorldServer world, int x, int y, int z, Block block, ItemStack stack, ForgeDirection facing) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.stack = stack;
            this.facing = facing;
        }
    }
}
