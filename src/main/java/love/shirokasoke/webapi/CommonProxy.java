package love.shirokasoke.webapi;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import love.shirokasoke.webapi.config.Configs;
import love.shirokasoke.webapi.config.ItemThreadConfig;
import love.shirokasoke.webapi.config.LocalizationConfig;
import love.shirokasoke.webapi.config.SecurityConfig;
import love.shirokasoke.webapi.config.ServerConfig;
import love.shirokasoke.webapi.config.StaticResourceConfig;
import love.shirokasoke.webapi.config.TickConfig;
import love.shirokasoke.webapi.config.UpdateConfig;
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
        Configs.init();
        MyMod.LOG.info("WebAPI preInit at version " + Tags.VERSION);
    }

    // load "Do your mod setup. Build whatever data structures you care about.
    // Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on
    // this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    public void loadComplete(FMLLoadCompleteEvent event) {
        if (ItemThreadConfig.enable) {
            new love.shirokasoke.webapi.thread.ItemsThread().start();
        }
    }

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        MyMod.LOG.info("Server Starting");
        Auth.init();
        WebServer.start(ServerConfig.httpPort, ServerConfig.nThreads);
        for (String i : SecurityConfig.disabledRoutes) {
            WebServer.removeRoute(i);
        }
        CloudflaredTunnel.start();
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerThreadDispatcher());
        ServerThreadDispatcher.setSlowTasksPerTick(TickConfig.maxPerTick);
        ServerThreadDispatcher.setBudgetMs(TickConfig.budgetMs);

        if (UpdateConfig.enableUpdateCheck) {
            new love.shirokasoke.webapi.thread.UpdateChecker().checkAsync();
        }
    }

    public void serverStarted(FMLServerStartedEvent event) {
        MyMod.LOG.info("Server Started");
        love.shirokasoke.webapi.webserver.handlers.recipe.Init.after();
        ItemStaticHandler s = new ItemStaticHandler(StaticResourceConfig.itemFile);
        if (s.isValid()) {
            ItemThreadConfig.enable = false;
            MyMod.LOG.info("ItemFile is valid, itemThread forcibly disabled");
            s.inject();
        }
        Lang.setup(LocalizationConfig.langFiles);
        love.shirokasoke.webapi.thread.TPSRecorder._start_();
    }

    // Called when the server is stopping
    public void serverStopping(FMLServerStoppingEvent event) {
        CloudflaredTunnel.stop();
        love.shirokasoke.webapi.thread.TPSRecorder._stop_();
        WebServer.stop();
    }
}
