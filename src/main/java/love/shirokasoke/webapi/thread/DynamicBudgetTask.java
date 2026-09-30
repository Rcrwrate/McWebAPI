package love.shirokasoke.webapi.thread;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.DimensionManager;

import cpw.mods.fml.common.FMLCommonHandler;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.config.TickConfig;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.Logs;

/**
 * 动态预算任务
 */
public class DynamicBudgetTask implements Runnable {

    private static volatile Future<?> future;

    // ---- 配置快照：任务启动时固定，运行期间视为不变 ----
    private final long intervalMs;
    private final double smoothing;
    private final int baseBudgetMs;
    private final Set<Integer> chargeDims;
    private final String chargeDimsDisplay;

    /** 上一次平滑后的动态预算 (ms) */
    private double smoothedBudgetMs = 0.0D;

    private DynamicBudgetTask() {
        this.intervalMs = TickConfig.dynamicBudgetInterval * 1000L;
        this.smoothing = Math.min(1.0D, Math.max(0.01D, TickConfig.dynamicBudgetSmoothing));
        this.baseBudgetMs = TickConfig.budgetMs;
        this.chargeDims = resolveChargeDims(TickConfig.dynamicBudgetDimIds);
        this.chargeDimsDisplay = chargeDims.isEmpty() ? "全部" : Arrays.toString(TickConfig.dynamicBudgetDimIds);
    }

    public static void _start_() {
        if (!TickConfig.dynamicBudget) return;
        if (future != null && !future.isDone()) return;
        future = BackgroundScheduler.submit("Dynamic-Budget", new DynamicBudgetTask());
    }

    public static void _stop_() {
        Future<?> f = future;
        future = null;
        BackgroundScheduler.cancel(f);
    }

    @Override
    public void run() {
        MyMod.LOG.info("已启动，采样间隔 {} 秒，计算维度: {}，平滑系数: {}", intervalMs / 1000L, chargeDimsDisplay, smoothing);
        try {
            while (true) {
                try {
                    adjustOnce();
                } catch (ConcurrentModificationException e) {
                    MyMod.LOG.debug("TickTime CME hit");
                } catch (Throwable e) {
                    MyMod.LOG.error("采样世界TickTime失败");
                    Logs.e(e);
                }

                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread()
                        .interrupt();
                    break;
                }
            }
        } finally {
            MyMod.LOG.info("[DynamicBudget] 已停止");
        }
    }

    /** 采样一次各维度平均 TickTime 并调整预算 */
    private void adjustOnce() {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) return;

        double totalWorldTickTimeMs = 0.0D;
        for (Integer dimId : DimensionManager.getIDs()) {
            if (!chargeDims.isEmpty() && !chargeDims.contains(dimId)) continue;

            long[] tickTimes = server.worldTickTimes.get(dimId);
            if (tickTimes == null || tickTimes.length == 0) continue;

            totalWorldTickTimeMs += mean(tickTimes) * 1.0E-6D;
        }

        double remaining = baseBudgetMs - totalWorldTickTimeMs;
        remaining = Math.max(1, Math.min((double) baseBudgetMs, remaining));

        smoothedBudgetMs = smoothedBudgetMs <= 0.0D ? remaining
            : smoothedBudgetMs * (1.0D - smoothing) + remaining * smoothing;

        ServerThreadDispatcher.setBudgetMs((int) Math.round(smoothedBudgetMs));
    }

    private static Set<Integer> resolveChargeDims(int[] dimIds) {
        Set<Integer> set = new HashSet<>();
        if (dimIds != null) {
            for (int id : dimIds) {
                set.add(id);
            }
        }
        return set;
    }

    private static long mean(long[] values) {
        long sum = 0L;
        for (long v : values) {
            sum += v;
        }
        return sum / values.length;
    }
}
