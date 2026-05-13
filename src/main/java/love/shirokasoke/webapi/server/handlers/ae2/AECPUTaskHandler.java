package love.shirokasoke.webapi.server.handlers.ae2;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.v2.CraftingJobV2;
import love.shirokasoke.webapi.utils.Items;

/**
 * 向 AE 合成网络提交自动合成任务
 *
 * <p>
 * <b>实现流程：</b>
 * <ol>
 * <li>校验请求方法为 POST，解析 JSON 请求体</li>
 * <li>通过坐标参数定位 AE 网络并初始化</li>
 * <li>根据 id + damage 构造 {@link ItemStack}，再包装为 {@link IAEItemStack}
 * 并设置合成数量</li>
 * <li>调用 {@link ICraftingGrid#beginCraftingJob} 异步计算合成计划</li>
 * <li>等待计算完成（兼容 V1/V2 两种计算器，30 秒超时）</li>
 * <li>若计算结果为 simulation（材料不足），返回失败</li>
 * <li>若指定了 CPU 名称，在可用 CPU 中查找匹配项</li>
 * <li>调用 {@link ICraftingGrid#submitJob} 将任务提交到 CPU</li>
 * <li>返回包含 bytes 消耗、CPU 名称、输出物品等信息的响应</li>
 * </ol>
 */
public class AECPUTaskHandler extends AEBaseHandler {

    /** 合成计算超时时间（毫秒） */
    private static final long CRAFTING_TIMEOUT_MS = 30000L;

    @Override
    public String getPath() {
        return "/ae/cpu/task";
    }

    @Override
    public String getDescription() {
        return "Submit AE crafting task. POST body: {id, damage, amount, cpu?}";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod()
            .equals("POST")) {
            throw new Error(400, "Method must be POST");
        }

        AEinit(exchange);

        JsonNode body = getBody(exchange);
        int id = body.path("id")
            .asInt(-1);
        int damage = body.path("damage")
            .asInt(0);
        long amount = body.path("amount")
            .asLong(1);
        String cpuName = body.path("cpu")
            .asText(null);

        if (id <= 0) {
            throw new Error(400, "Missing or invalid 'id' in request body");
        }
        if (amount <= 0) {
            throw new Error(400, "Invalid 'amount' in request body");
        }

        Item item = Item.getItemById(id);
        if (item == null) {
            throw new Error(404, "Item not found for id: " + id);
        }

        ItemStack stack = new ItemStack(item, 1, damage);
        IAEItemStack craftWhat = AEApi.instance()
            .storage()
            .createItemStack(stack);
        if (craftWhat == null) {
            throw new Error(500, "Failed to create AEItemStack");
        }
        craftWhat.setStackSize(amount);

        // 获取合成网格
        ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
        if (craftingGrid == null) {
            throw new Error(500, "Crafting grid not available");
        }

        // 构造 ActionSource
        /**
         * BaseActionSource 不能为 null，否则后续 CPU 筛选和权限检查会 NPE
         */
        BaseActionSource src;
        if (host instanceof IActionHost) {
            src = new MachineSource((IActionHost) host);
        } else {
            throw new Error(500, "Host does not support action source");
        }

        // 开始异步计算合成计划
        Future<ICraftingJob> future = craftingGrid.beginCraftingJob(world, grid, src, craftWhat, null);
        ICraftingJob job = waitForJob(future);

        // 若计算结果为 simulation，说明材料不足或该物品无法合成
        if (job.isSimulation()) {
            throw new Error(500, "Failed to simulate job (Materials missing or craft not possible)");
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
                throw new Error(404, "CPU not found: " + cpuName);
            }
        }

        // 提交合成任务到目标 CPU
        ICraftingLink link = craftingGrid.submitJob(job, null, targetCpu, true, src);
        if (link == null) {
            throw new Error(500, "Failed to submit job to CPU (no available CPU or insufficient resources)");
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("bytes", job.getByteTotal());
        response.put("cpu", targetCpu != null ? targetCpu.getName() : "auto");
        response.set(
            "output",
            Items.dump(
                job.getOutput()
                    .getItemStack())
                .put(
                    "stackSize",
                    job.getOutput()
                        .getStackSize()));
        sendResponse(exchange, response);
    }

    /**
     * 等待异步合成计算完成。
     *
     * <p>
     * AE2 存在两种合成计算器版本：
     * <ul>
     * <li><b>V1 (CraftingJob)</b>：基于 Future.get() 阻塞等待</li>
     * <li><b>V2 (CraftingJobV2)</b>：基于 simulateFor() 轮询计算进度，需手动控制超时</li>
     * </ul>
     *
     * @param future beginCraftingJob 返回的 Future
     * @return 计算完成的 ICraftingJob
     * @throws Error 计算超时、被中断或发生异常时抛出
     */
    private ICraftingJob waitForJob(Future<ICraftingJob> future) throws Error {
        try {
            // V2 计算器：CraftingJobV2 自身实现了 Future 接口，需要轮询 simulateFor
            if (future instanceof CraftingJobV2) {
                CraftingJobV2 v2 = (CraftingJobV2) future;
                long deadline = System.currentTimeMillis() + CRAFTING_TIMEOUT_MS;
                while (v2.simulateFor(100) && System.currentTimeMillis() < deadline) {
                    // 每次让出 100ms 给计算线程，直到完成或超时
                }
                if (!v2.isDone()) {
                    v2.cancel(true);
                    throw new Error(504, "Crafting calculation timed out");
                }
                return v2;
            }
            // V1 计算器：直接使用 Future.get 阻塞等待，带超时
            return future.get(CRAFTING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new Error(504, "Crafting calculation timed out");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread()
                .interrupt();
            throw new Error(504, "Crafting calculation interrupted");
        } catch (Exception e) {
            future.cancel(true);
            throw new Error(504, "Crafting calculation failed: " + e.getMessage());
        }
    }
}
