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

    /** 每tick从慢队列中执行的最大任务数 */
    private static int slowTasksPerTick = 1;

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
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
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
        // 慢队列：每tick执行指定数量
        for (int i = 0; i < slowTasksPerTick; i++) {
            Runnable task = slowQueue.poll();
            if (task == null) {
                break;
            }
            try {
                task.run();
            } catch (Exception e) {
                // 慢队列为非阻塞模式，仅记录异常
                love.shirokasoke.webapi.MyMod.LOG.error("[ServerThreadDispatcher] Slow queue task failed", e);
            }
        }
    }
}
