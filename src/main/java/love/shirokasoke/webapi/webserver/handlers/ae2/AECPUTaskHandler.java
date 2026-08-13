package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.v2.CraftingJobV2;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.NBT;
import love.shirokasoke.webapi.webserver.Context;

/**
 * 向 AE 合成网络提交自动合成任务
 *
 * <li>构造 {@link ItemStack}，再包装为 {@link IAEItemStack} 并设置合成数量</li>
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
            throw new ApiException(400, "Method must be POST");
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

        // 开始异步计算合成计划
        Future<ICraftingJob> future = craftingGrid.beginCraftingJob(context.world, grid, src, craftWhat, null);
        ICraftingJob<?> job = waitForJob(future);

        // 若计算结果为 simulation，说明材料不足或该物品无法合成
        if (job.isSimulation()) {
            throw new ApiException(500, "Failed to simulate job (Materials missing or craft not possible)");
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
        ICraftingLink link = craftingGrid.submitJob(job, null, targetCpu, true, src);
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
     * @throws ApiException 计算超时、被中断或发生异常时抛出
     */
    private ICraftingJob<?> waitForJob(Future<ICraftingJob> future) throws ApiException {
        try {
            // V2 计算器：CraftingJobV2 自身实现了 Future 接口，需要轮询 simulateFor
            if (future instanceof CraftingJobV2 v2) {
                long deadline = System.currentTimeMillis() + CRAFTING_TIMEOUT_MS;
                while (v2.simulateFor(100) && System.currentTimeMillis() < deadline) {
                    // 每次让出 100ms 给计算线程，直到完成或超时
                }
                if (!v2.isDone()) {
                    v2.cancel(true);
                    throw new ApiException(504, "Crafting calculation timed out");
                }
                return v2;
            }
            // V1 计算器：直接使用 Future.get 阻塞等待，带超时
            return future.get(CRAFTING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new ApiException(504, "Crafting calculation timed out");
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
}
