package love.shirokasoke.webapi;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import love.shirokasoke.webapi.server.Lang;
import love.shirokasoke.webapi.webserver.Auth;
import love.shirokasoke.webapi.webserver.WebServer;
import love.shirokasoke.webapi.webserver.handlers.item.ItemStaticHandler;

public class CommonProxy {

    private static final ConcurrentLinkedQueue<PendingTask> pendingTasks = new ConcurrentLinkedQueue<>();

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

    /**
     * 将任务投递到服务器主线程执行，并阻塞等待完成。
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

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
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
    }

    // preInit "Run before anything else. Read your config, create blocks, items,
    // etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        // Create custom config file path: config/shirokasoke/WebAPI.cfg
        File configDir = new File(event.getModConfigurationDirectory(), "shirokasoke");
        if (!configDir.exists()) {
            configDir.mkdirs();
            MyMod.LOG.info("Created config directory: " + configDir.getAbsolutePath());
        }
        File configFile = new File(configDir, "WebAPI.cfg");

        Config.synchronizeConfiguration(configFile);
        MyMod.LOG.info("Configuration loaded from: " + configFile.getAbsolutePath());
        MyMod.LOG.info("WebAPI preInit at version " + Tags.VERSION);
    }

    // load "Do your mod setup. Build whatever data structures you care about.
    // Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on
    // this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    public void loadComplete(FMLLoadCompleteEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        MyMod.LOG.info("Server Starting");
        Auth.setup(Config.authUrlPrefixes);
        WebServer.start(Config.httpPort, Config.nThreads);
        Lang.setup(Config.langFiles);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    public void serverStarted(FMLServerStartedEvent event) {
        MyMod.LOG.info("Server Started");
        ItemStaticHandler s = new ItemStaticHandler(Config.ItemFile);
        if (s.isValid()) {
            Config.itemThreadEnable = false;
            MyMod.LOG.info("ItemFile is valid, itemThread forcibly disabled");
            s.inject();
        }
        if (Config.itemThreadEnable) {
            new love.shirokasoke.webapi.thread.ItemsThread().start();
        }

    }

    // Called when the server is stopping
    public void serverStopping(FMLServerStoppingEvent event) {
        // 停止HTTP服务器
        WebServer.stop();
    }
}
