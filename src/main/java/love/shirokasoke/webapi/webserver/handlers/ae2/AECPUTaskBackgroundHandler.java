package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingJobV2;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.resolvers.CraftingTask;
import love.shirokasoke.webapi.crafting.OffTickCraftingJobV2;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.Logs;
import love.shirokasoke.webapi.utils.NBT;
import love.shirokasoke.webapi.utils.Pattern;
import love.shirokasoke.webapi.webserver.Context;

/**
 * 实验性
 * <p>
 * 向 AE 合成网络提交自动合成任务，将大部分内容放置在 Web 线程直接进行
 */
public class AECPUTaskBackgroundHandler extends AEBaseHandler {

    /** 合成计算超时时间（毫秒） */
    private static final long CRAFTING_TIMEOUT_MS = 30000L;

    /** 请求树递归展开的最大深度，超过则截断（防异常深的链条打爆栈） */
    private static final int MAX_TREE_DEPTH = 256;

    @Override
    public String getPath() {
        return "/ae/cpu/task/background";
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void run(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod()
            .equals("POST")) {
            throw new ApiException(405, "Method must be POST");
        }

        Context context = AEinit(exchange);
        IGrid grid = context.grid;
        IGridHost host = context.host;

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
        BaseActionSource src;
        if (host instanceof IActionHost) {
            src = new MachineSource((IActionHost) host);
        } else {
            throw new ApiException(500, "Host does not support action source");
        }

        // 1) 主线程构造 job：父类构造器读取网络库存与样板表完成快照（毫秒级）。
        // craftWhat 为 IAEStack<?>，与 AE2 自身一致使用原始类型构造（见 CraftingGridCache）
        OffTickCraftingJobV2 job;
        try {
            job = ServerThreadDispatcher.callOnServerThread(
                () -> new OffTickCraftingJobV2(
                    context.world,
                    grid,
                    src,
                    (IAEStack) craftWhat,
                    CraftingMode.STANDARD,
                    null));
        } catch (Exception e) {
            Logs.e(e);
            throw new ApiException(503, e.getMessage());
        }

        // 2) Web 线程直接分片计算合成树（job 私有且自包含，不占用服务器 tick 预算）。
        // 计算过程中的异常由 simulateFor 内部捕获并转为 CANCELLED + errorMessage
        job.compute(CRAFTING_TIMEOUT_MS);

        // 计算被取消：errorMessage 为空是超时自取消，非空是计算过程抛错
        if (job.isCancelled()) {
            String errorMessage = job.getErrorMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                throw new ApiException(504, "Crafting calculation timed out");
            }
            throw new ApiException(500, "Crafting calculation failed: " + errorMessage);
        }

        // 若计算结果为 simulation，说明材料不足或该物品无法合成
        if (job.isSimulation()) {
            throw new ApiException(500, "Failed to simulate job (Materials missing or craft not possible)");
        }

        // 3) CPU 查找（getCpus）与任务提交访问网格状态，放在服务器主线程执行
        ICraftingLink link;
        try {
            link = ServerThreadDispatcher.callOnServerThread(() -> {
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
                return craftingGrid.submitJob(job, null, targetCpu, true, src);
            });
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            Logs.e(e);
            throw new ApiException(503, e.getMessage());
        }
        if (link == null) {
            throw new ApiException(500, "Failed to submit job to CPU (no available CPU or insufficient resources)");
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("bytes", job.getByteTotal());
        // 指定 CPU 名称时提交成功即已按名匹配（getName() 与 cpuName 相同），否则为自动分配
        response.put("cpu", cpuName != null && !cpuName.isEmpty() ? cpuName : "auto");
        response.set(
            "output",
            Items.dump(
                job.getOutput()
                    .getItemStackForNEI())
                .put(
                    "stackSize",
                    job.getOutput()
                        .getStackSize()));
        response.set("tree", dumpTree(job));
        sendResponse(exchange, response);
    }

    /**
     * 序列化合成树
     *
     * <ul>
     * <li>{@code requests}：请求树，从根请求递归展开</li>
     * <li>{@code tasks}：按执行顺序的任务列表(重复内容，仅供调试)</li>
     * </ul>
     */
    private ObjectNode dumpTree(CraftingJobV2<?> job) {
        final CraftingContext context = job.getContext();
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
    private ObjectNode dumpRequest(CraftingRequest request,
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
                "taskType",
                entry.task.getClass()
                    .getSimpleName());
            resolver.put(
                "taskState",
                entry.task.getState()
                    .name());
            resolver.put("taskSimulated", entry.task.isSimulated());
            resolver.put("taskDetail", entry.task.toString());
            resolver.set(
                "resolved",
                Pattern.dumpAEStack(entry.resolvedStack)
                    .put("stackSize", entry.resolvedStack.getStackSize()));
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
