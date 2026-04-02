package love.shirokasoke.webapi;

import java.io.File;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import love.shirokasoke.webapi.server.WebServer;

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

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        MyMod.LOG.info("Server Starting");
        WebServer.start(Config.httpPort, Config.nThreads);

    }

    public void serverStarted(FMLServerStartedEvent event) {
        MyMod.LOG.info("Server Started");
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
