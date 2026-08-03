package love.shirokasoke.webapi;

import java.io.File;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import love.shirokasoke.webapi.server.Lang;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.thread.CloudflaredTunnel;
import love.shirokasoke.webapi.webserver.Auth.Auth;
import love.shirokasoke.webapi.webserver.WebServer;
import love.shirokasoke.webapi.webserver.handlers.item.ItemStaticHandler;

public class CommonProxy {

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
        Auth.init();
        WebServer.start(Config.httpPort, Config.nThreads);
        for (String i : Config.disabledRoutes) {
            WebServer.removeRoute(i);
        }
        CloudflaredTunnel.start();
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerThreadDispatcher());
        ServerThreadDispatcher.setSlowTasksPerTick(Config.MaxPerTick);
        ServerThreadDispatcher.setBudgetMs(Config.budgetMs);

        if (Config.enableUpdateCheck) {
            new love.shirokasoke.webapi.thread.UpdateChecker().checkAsync();
        }
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
        Lang.setup(Config.langFiles);
        love.shirokasoke.webapi.thread.TPSRecorder._start_();
    }

    // Called when the server is stopping
    public void serverStopping(FMLServerStoppingEvent event) {
        CloudflaredTunnel.stop();
        love.shirokasoke.webapi.thread.TPSRecorder._stop_();
        WebServer.stop();
    }
}
