package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.AEApi;
import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.fast.CraftingJobFast;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingJobV2;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.resolvers.CraftingTask;
import love.shirokasoke.webapi.crafting.OffTickCraftingJobV2;
import love.shirokasoke.webapi.mixins.MixinConfig;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.Logs;
import love.shirokasoke.webapi.utils.NBT;
import love.shirokasoke.webapi.utils.Pattern;
import love.shirokasoke.webapi.webserver.Context;

/**
 * 向 AE 合成网络提交自动合成任务
 *
 * <li>构造 {@link ItemStack}，再包装为 {@link IAEItemStack} 并设置合成数量</li>
 * <li>在服务器主线程调用 {@link ICraftingGrid#beginCraftingJob} 注册合成计划</li>
 * <li>被动等待计算完成：计算由 AE2 TickHandler 在主线程分时片驱动，回调通知，30 秒超时</li>
 * <li>若计算结果为 simulation（材料不足），返回失败</li>
 * <li>若指定了 CPU 名称，在可用 CPU 中查找匹配项</li>
 * <li>调用 {@link ICraftingGrid#submitJob} 将任务提交到 CPU</li>
 * <li>返回包含 bytes 消耗、CPU 名称、输出物品等信息的响应</li>
 * </ol>
 */
public class AECPUTaskHandler extends AEBaseHandler {

    /** 合成计算超时时间（毫秒） */
    private static final long CRAFTING_TIMEOUT_MS = 30000L;
    /** 是否允许跨线程计算 */
    private static final boolean OffThread = MixinConfig.late.enableAECraftJoboffThread;
    /** 请求树递归展开的最大深度，超过则截断（防异常深的链条打爆栈） */
    private static final int MAX_TREE_DEPTH = 256;

    @Override
    public String getPath() {
        return "/ae/cpu/task";
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void run(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod()
            .equals("POST")) {
            throw new ApiException(405, "Method must be POST");
        }
        final Map<String, String> query = parseQueryParams(exchange);
        final int detailMode = switch (query.getOrDefault("detail", "fail")) {
            case "all" -> 2;
            case "fail" -> 1;
            case "none" -> 0;
            default -> 1;
        };
        final Context context = AEinit(query);
        final IGrid grid = context.grid;
        final IGridHost host = context.host;

        JsonNode json = getBody(exchange);

        String cpuName = json.path("cpu")
            .asText(null);

        int id = json.path("id")
            .asInt(-1);
        if (id < 0) {
            throw new ApiException(400, "invalid field 'id'");
        }
        boolean isItem = !"fluid".equals(
            json.path("Type")
                .asText("item"));
        long count = json.path("Count")
            .asLong(-1);
        if (count < 0) {
            throw new ApiException(400, "invalid field 'Count'");
        }
        IAEStack<?> craftWhat;
        if (isItem) {
            ItemStack stack = NBT.toItemStack(
                id,
                json.path("Damage")
                    .asInt(0),
                json.path("tag")
                    .asText(null));
            craftWhat = AEApi.instance()
                .storage()
                .createItemStack(stack)
                .setStackSize(count);
        } else {
            Fluid f = FluidRegistry.getFluid(
                json.get("id")
                    .asInt());
            FluidStack fs = new FluidStack(f, 1);
            craftWhat = AEApi.instance()
                .storage()
                .createFluidStack(fs)
                .setStackSize(count);
        }

        if (craftWhat == null) {
            throw new ApiException(500, "Failed to create AEItemStack");
        }

        // 获取合成网格
        ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
        if (craftingGrid == null) {
            throw new ApiException(500, "Crafting grid not available");
        }

        // 构造 ActionSource
        /**
         * BaseActionSource 不能为 null，否则后续 CPU 筛选和权限检查会 NPE
         */
        final BaseActionSource src;
        if (host instanceof IActionHost AH) {
            src = new MachineSource(AH);
        } else {
            throw new ApiException(500, "Host does not support action source");
        }

        final CountDownLatch calculated = new CountDownLatch(1);
        final ICraftingCallback callback = j -> calculated.countDown();
        final Future<ICraftingJob> future;
        try {
            if (OffThread) {
                // 手动创建
                future = new OffTickCraftingJobV2(context.world, grid, src, craftWhat, CraftingMode.STANDARD, callback)
                    .schedule();
            } else {
                future = ServerThreadDispatcher.callOnServerThread(
                    () -> craftingGrid.beginCraftingJob(context.world, grid, src, craftWhat, callback));
            }
        } catch (Exception e) {
            Logs.e(e);
            throw new ApiException(503, e.getMessage());
        }

        // 被动等待计算完成
        ICraftingJob<?> job = waitForJob(future, calculated);

        // 若计算结果为 simulation，说明材料不足或该物品无法合成
        if (job.isSimulation()) {
            throw new ApiException(
                500,
                "Failed to simulate job (Materials missing or craft not possible)",
                detailMode >= 1 ? dumpTree(job) : null);
        }

        // 若请求中指定了 CPU，按名称查找；未指定则自动分配
        ICraftingCPU targetCpu = null;
        if (cpuName != null && !cpuName.isEmpty()) {
            for (ICraftingCPU cpu : craftingGrid.getCpus()) {
                if (cpuName.equals(cpu.getName())) {
                    targetCpu = cpu;
                    break;
                }
            }
            if (targetCpu == null) {
                throw new ApiException(404, "CPU not found: " + cpuName);
            }
        }

        // 提交合成任务到目标 CPU
        ICraftingLink link;
        try {
            final ICraftingCPU cpu = targetCpu;
            link = ServerThreadDispatcher.callOnServerThread(() -> craftingGrid.submitJob(job, null, cpu, true, src));
        } catch (Exception e) {
            Logs.e(e);
            throw new ApiException(503, e.getMessage());
        }
        if (link == null) {
            throw new ApiException(500, "Failed to submit job to CPU (no available CPU or insufficient resources)");
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("bytes", job.getByteTotal());
        response.put("cpu", targetCpu != null ? targetCpu.getName() : "auto");
        response.set(
            "output",
            Items.dump(
                job.getOutput()
                    .getItemStackForNEI())
                .put(
                    "stackSize",
                    job.getOutput()
                        .getStackSize()));
        if (detailMode >= 2) response.set("job", dumpTree(job));
        sendResponse(exchange, response);
    }

    /**
     * 被动等待合成计算完成
     * 
     * @apiNote 实际上只会使用V2版本，Fast版本后续可以添加参数控制
     */
    private ICraftingJob<?> waitForJob(@SuppressWarnings("rawtypes") Future<ICraftingJob> future,
        CountDownLatch calculated) throws ApiException {
        try {
            if (future instanceof OffTickCraftingJobV2 off) {
                off.simulateFor((int) CRAFTING_TIMEOUT_MS);
                if (!off.isDone()) {
                    throw new ApiException(504, "Crafting calculation timed out");
                }
                return off;
            }
            // V2 计算器：等待主线程分时片计算完成，由回调经 latch 通知
            else if (future instanceof CraftingJobV2 v2) {
                if (!calculated.await(CRAFTING_TIMEOUT_MS, TimeUnit.MILLISECONDS) && !v2.isDone()) {
                    v2.cancel(true);
                    throw new ApiException(504, "Crafting calculation timed out");
                }
                // 计算在主线程抛出异常时 state 为 CANCELLED，errorMessage 记录原因
                if (v2.isCancelled()) {
                    throw new ApiException(500, "Crafting calculation failed: " + v2.getErrorMessage());
                }
                return v2;
            }
            // lite (CraftingJobFast)：schedule() 时已同步计算完成，直接获取
            return future.get(CRAFTING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread()
                .interrupt();
            throw new ApiException(504, "Crafting calculation interrupted");
        } catch (Exception e) {
            future.cancel(true);
            throw new ApiException(504, "Crafting calculation failed: " + e.getMessage());
        }
    }

    // region 合成树

    /**
     * 序列化合成树
     *
     * <ul>
     * <li>{@code requests}：请求树，从根请求递归展开</li>
     * <li>{@code tasks}：按执行顺序的任务列表(重复内容，仅供调试)</li>
     * </ul>
     */
    private static ObjectNode dumpTree(ICraftingJob<?> job) {
        final CraftingContext context;
        switch (job) {
            case CraftingJobV2<?> j-> context = j.getContext();
            case CraftingJobFast<?> j-> context = j.getContext();
            default -> {
                return null;
            }
        }
        final ObjectNode tree = mapper.createObjectNode();

        // 按父请求分组，用于自顶向下展开请求树
        final Map<CraftingRequest, List<CraftingRequest>> childrenByParent = new IdentityHashMap<>();
        for (CraftingContext.RequestInProcessing processing : context.getLiveRequests()) {
            childrenByParent.computeIfAbsent(processing.request.parentRequest, key -> new ArrayList<>())
                .add(processing.request);
        }

        final ArrayNode roots = mapper.createArrayNode();
        for (CraftingContext.RequestInProcessing processing : context.getLiveRequests()) {
            if (processing.request.parentRequest == null) {
                roots.add(dumpRequest(processing.request, childrenByParent, 0));
            }
        }
        tree.set("requests", roots);

        final ArrayNode tasks = mapper.createArrayNode();
        for (CraftingTask task : context.getResolvedTasks()) {
            final ObjectNode node = mapper.createObjectNode();
            node.put(
                "type",
                task.getClass()
                    .getSimpleName());
            node.put(
                "state",
                task.getState()
                    .name());
            node.put("simulated", task.isSimulated());
            node.put("priority", task.priority);
            node.put("detail", task.toString());
            tasks.add(node);
        }
        tree.set("tasks", tasks);

        return tree;
    }

    /**
     * 递归序列化单个请求节点：自身信息 + resolver 链 + 子请求
     *
     * @param depth 当前深度，超过 {@link #MAX_TREE_DEPTH} 截断
     */
    private static ObjectNode dumpRequest(CraftingRequest request,
        Map<CraftingRequest, List<CraftingRequest>> childrenByParent, int depth) {
        final ObjectNode node = mapper.createObjectNode();
        node.set(
            "stack",
            Pattern.dumpAEStack(request.stack)
                .put("stackSize", request.stack.getStackSize()));
        node.put("substitutionMode", request.substitutionMode.name());
        node.put("remaining", request.remainingToProcess);
        node.put("byteCost", request.getByteCost());
        node.put("simulated", request.wasSimulated);
        node.put("incomplete", request.incomplete);
        node.put("detail", request.toString());

        final ArrayNode resolvers = mapper.createArrayNode();
        for (CraftingRequest.UsedResolverEntry entry : request.usedResolvers) {
            final ObjectNode resolver = mapper.createObjectNode();
            resolver.put(
                "type",
                entry.task.getClass()
                    .getSimpleName());
            resolver.put(
                "state",
                entry.task.getState()
                    .name());
            resolver.put("simulated", entry.task.isSimulated());
            resolver.put("detail", entry.task.toString());
            resolver.put("resolved", entry.resolvedStack.getStackSize());
            resolvers.add(resolver);
        }
        node.set("resolvers", resolvers);

        final List<CraftingRequest> children = childrenByParent.get(request);
        if (children != null && !children.isEmpty()) {
            if (depth >= MAX_TREE_DEPTH) {
                node.put("childrenTruncated", children.size());
            } else {
                final ArrayNode childNodes = mapper.createArrayNode();
                for (CraftingRequest child : children) {
                    childNodes.add(dumpRequest(child, childrenByParent, depth + 1));
                }
                node.set("children", childNodes);
            }
        }
        return node;
    }
}
