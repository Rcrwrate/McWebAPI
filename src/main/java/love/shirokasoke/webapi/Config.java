package love.shirokasoke.webapi;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static int httpPort = 40002;
    public static int nThreads = 10;
    public static boolean classDump = false;
    public static int itemThreadDelayMs = 10;
    public static int itemThreadBatchSize = 100;
    public static boolean itemThreadEnable = true;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        httpPort = configuration
            .getInt("httpPort", Configuration.CATEGORY_GENERAL, httpPort, 1024, 65535, "HTTP server port");
        nThreads = configuration.getInt("nThreads", Configuration.CATEGORY_GENERAL, 10, 4, 36, "WebServer Threads");

        classDump = configuration.getBoolean("classDump", "debug", false, "allow class dump");

        itemThreadEnable = configuration.getBoolean("enable", "itemThread", true, "enable Itemscache auto build");
        itemThreadDelayMs = configuration.getInt(
            "DelayMs",
            "itemThread",
            10,
            0,
            1000,
            "ItemsThread processing delay in milliseconds (0 to disable, higher = slower but less memory pressure)");
        itemThreadBatchSize = configuration.getInt(
            "BatchSize",
            "itemThread",
            100,
            10,
            1000,
            "ItemsThread batch size for GC hints (items processed before System.gc() call)");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
