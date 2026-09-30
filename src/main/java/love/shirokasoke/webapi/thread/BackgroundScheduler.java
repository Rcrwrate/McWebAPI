package love.shirokasoke.webapi.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.config.ServerConfig;

/**
 * 统一的后台任务调度器
 */
public final class BackgroundScheduler {

    private static final ExecutorService EXECUTOR = createExecutor();

    private BackgroundScheduler() {}

    private static ExecutorService createExecutor() {
        if (ServerConfig.useVirtualThreads) {
            try {
                ThreadFactory virtualFactory = Thread.ofVirtual()
                    .name("Web-BG-", 1)
                    .factory();
                return Executors.newThreadPerTaskExecutor(virtualFactory);
            } catch (Throwable t) {
                // Java < 21 时 Thread.ofVirtual / newThreadPerTaskExecutor 不存在，
                // 抛出 NoSuchMethodError 等，此处捕获并回退
                MyMod.LOG.warn("[BackgroundScheduler] 虚拟线程不可用（{}），回退为平台守护线程池", t.toString());
            }
        }
        // 平台线程回退：仅使用 Java 8 API，保证旧 JVM 可用
        AtomicInteger seq = new AtomicInteger(1);
        return Executors.newCachedThreadPool(task -> {
            Thread t = new Thread(task, "Web-BG-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 提交一个后台任务（长生命周期任务需内部自带循环与 sleep）。
     */
    public static Future<?> submit(String taskName, Runnable task) {
        return EXECUTOR.submit(() -> {
            Thread.currentThread()
                .setName(taskName);
            try {
                task.run();
            } catch (Throwable t) {
                MyMod.LOG.error("[BackgroundScheduler] 任务 {} 异常退出", taskName, t);
            }
        });
    }

    /** 取消任务：中断其线程，任务内部的 sleep 会抛 InterruptedException 并退出循环 */
    public static void cancel(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }
}
