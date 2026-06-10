package love.shirokasoke.webapi.server;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ServerThreadDispatcher {

    private static final ConcurrentLinkedQueue<PendingTask> pendingTasks = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<PendingCallable<?>> pendingCallables = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<Runnable> slowQueue = new ConcurrentLinkedQueue<>();

    /** 每tick从慢队列中执行的最大任务数（硬上限） */
    private static int slowTasksPerTick = 1000;

    /** 每tick慢队列执行的时间预算上限 (ms)，不超过此值以避免影响 TPS */
    private static int slowQueueBudgetMs = 50;

    /** 上一 tick 的实际耗时 (ns)，用于动态调整预算 */
    private static long lastTickDurationNanos = 0;
    private static long lastTickStartNanos = 0;

    private static class PendingTask {

        final Runnable task;
        final CountDownLatch latch;
        final AtomicReference<Exception> error;

        PendingTask(Runnable task) {
            this.task = task;
            this.latch = new CountDownLatch(1);
            this.error = new AtomicReference<>();
        }
    }

    private static class PendingCallable<T> {

        final Callable<T> task;
        final CountDownLatch latch;
        final AtomicReference<Exception> error;
        final AtomicReference<T> result;

        PendingCallable(Callable<T> task) {
            this.task = task;
            this.latch = new CountDownLatch(1);
            this.error = new AtomicReference<>();
            this.result = new AtomicReference<>();
        }
    }

    /**
     * 将任务投递到服务器主线程立即执行，并阻塞等待完成。
     * 如果当前线程已经是 Server thread，则直接执行。
     */
    public static void runOnServerThread(Runnable task) throws Exception {
        if ("Server thread".equals(
            Thread.currentThread()
                .getName())) {
            task.run();
            return;
        }
        PendingTask pt = new PendingTask(task);
        pendingTasks.offer(pt);
        pt.latch.await();
        Exception ex = pt.error.get();
        if (ex != null) {
            throw ex;
        }
    }

    /**
     * 将带返回值的任务投递到服务器主线程立即执行，并阻塞等待返回结果。
     * 如果当前线程已经是 Server thread，则直接执行并返回。
     */
    public static <T> T callOnServerThread(Callable<T> task) throws Exception {
        if ("Server thread".equals(
            Thread.currentThread()
                .getName())) {
            return task.call();
        }
        PendingCallable<T> pc = new PendingCallable<>(task);
        pendingCallables.offer(pc);
        pc.latch.await();
        Exception ex = pc.error.get();
        if (ex != null) {
            throw ex;
        }
        return pc.result.get();
    }

    /**
     * 将任务投递到服务器主线程的慢队列，每tick仅执行 {@code slowTasksPerTick} 个任务。
     * 此方法不阻塞，调用后立即返回。
     */
    public static void scheduleOnServerThread(Runnable task) {
        slowQueue.offer(task);
    }

    /** 设置每tick从慢队列中执行的最大任务数 */
    public static void setSlowTasksPerTick(int count) {
        slowTasksPerTick = Math.max(1, count);
    }

    /** 设置每tick慢队列执行的时间预算上限 (ms) */
    public static void setSlowQueueBudgetMs(int ms) {
        slowQueueBudgetMs = Math.max(1, ms);
    }

    /** 获取慢队列中待执行的任务数 */
    public static int getSlowQueueSize() {
        return slowQueue.size();
    }

    private static <T> void executeCallable(PendingCallable<T> pc) throws Exception {
        T result = pc.task.call();
        pc.result.set(result);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            // 记录上一 tick 总耗时（如果有的话）
            if (lastTickStartNanos > 0) {
                lastTickDurationNanos = System.nanoTime() - lastTickStartNanos;
            }
            lastTickStartNanos = System.nanoTime();

            // 立即队列：全部执行
            PendingTask pt;
            while ((pt = pendingTasks.poll()) != null) {
                try {
                    pt.task.run();
                } catch (Exception e) {
                    pt.error.set(e);
                } finally {
                    pt.latch.countDown();
                }
            }
            // 立即队列（带返回值）：全部执行
            PendingCallable<?> pc;
            while ((pc = pendingCallables.poll()) != null) {
                try {
                    executeCallable(pc);
                } catch (Exception e) {
                    pc.error.set(e);
                } finally {
                    pc.latch.countDown();
                }
            }
            // 慢队列：保底 + 时间预算
            executeSlowQueue();
        }
    }

    /**
     * 执行慢队列任务。
     * 双重硬限制：数量不超过 slowTasksPerTick，时间不超过动态预算。
     * 预算 = min(slowQueueBudgetMs, 50ms - 上一tick占用时间)，
     * 任一条件达到即停止本 tick 执行。
     */
    private static void executeSlowQueue() {
        long tickNanos = 50_000_000L; // 50ms = 标准 tick
        long usedLastTick = lastTickDurationNanos;
        long budgetNanos = Math.min(tickNanos - usedLastTick, (long) slowQueueBudgetMs * 1_000_000L);
        budgetNanos = Math.max(0, budgetNanos);

        long start = System.nanoTime();
        int executed = 0;
        while (executed < slowTasksPerTick) {
            if (System.nanoTime() - start >= budgetNanos) {
                break;
            }
            Runnable task = slowQueue.poll();
            if (task == null) {
                break;
            }
            try {
                task.run();
            } catch (Exception e) {
                love.shirokasoke.webapi.MyMod.LOG.error("[ServerThreadDispatcher] Slow queue task failed", e);
            }
            executed++;
        }
    }
}
