package love.shirokasoke.webapi;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static int httpPort = 40002;
    public static int nThreads = 10;

    public static boolean classDump = false;

    public static String ItemFile = "dumps/items.json";
    public static String ItemIconFolder = "dumps/item_icons";
    public static int itemThreadDelayMs = 10;
    public static int itemThreadBatchSize = 100;
    public static boolean itemThreadEnable = true;

    public static String BlockFile = "dumps/blocks.json";
    public static String BlockTileFolder = "dumps/block_tiles";

    public static String FluidIconFolder = "dumps/fluid_icons";

    public static String authToken = "";
    public static String[] authUrlPrefixes = new String[] { "/setblock|GET|POST", "/chunk/force|GET|POST" };
    public static String[] langFiles = new String[] { "assets/minecraft/lang/zh_CN.lang" };

    // client
    public static int itemIconDelayMs = 10;
    public static int itemIconSize = 256;

    public static int blockTileDelayMs = 10;
    public static int blockTileSize = 64;

    public static int fluidIconDelayMs = 10;
    public static int fluidIconSize = 256;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        httpPort = configuration.getInt("httpPort", "server", httpPort, 1024, 65535, "HTTP server port");
        nThreads = configuration.getInt("nThreads", "server", 10, 4, 36, "WebServer Threads");

        classDump = configuration.getBoolean("classDump", "debug", classDump, "allow class dump");

        ItemFile = configuration.getString(
            "ItemFile",
            "server",
            ItemFile,
            "if ItemFile is set and valid, itemThread will be disable forcely");
        ItemIconFolder = configuration
            .getString("ItemIconFolder", "server", ItemIconFolder, "Client dumped Item Icon Folder");
        BlockFile = configuration.getString(
            "BlockFile",
            "server",
            BlockFile,
            "if BlockFile is set and valid, block data will be loaded from this file");
        BlockTileFolder = configuration
            .getString("BlockTileFolder", "server", BlockTileFolder, "Client dumped Block Tile Folder");
        FluidIconFolder = configuration
            .getString("FluidIconFolder", "server", FluidIconFolder, "Client dumped Fluid Icon Folder");
        itemThreadEnable = configuration
            .getBoolean("enable", "server.itemThread", itemThreadEnable, "enable Itemscache auto build");
        itemThreadDelayMs = configuration.getInt(
            "DelayMs",
            "server.itemThread",
            itemThreadDelayMs,
            0,
            1000,
            "ItemsThread processing delay in milliseconds (0 to disable, higher = slower but less memory pressure)");
        itemThreadBatchSize = configuration.getInt(
            "BatchSize",
            "server.itemThread",
            itemThreadBatchSize,
            10,
            1000,
            "ItemsThread batch size for GC hints (items processed before System.gc() call)");

        authToken = configuration
            .getString("authToken", "server.security", authToken, "simple use to auth HTTP request");
        authUrlPrefixes = configuration.getStringList(
            "authUrlPrefixes",
            "server.security",
            authUrlPrefixes,
            "List of URL prefixes (e.g., '/setblock|GET|POST', '/chunk/force|GET|POST') which need auth");
        langFiles = configuration.getStringList(
            "langFiles",
            "localization",
            langFiles,
            "List of .lang files to inject into server localization (relative to classpath root, e.g. 'assets/minecraft/lang/zh_CN.lang', 'assets/forge/lang/zh_CN.lang')");

        // client
        itemIconDelayMs = configuration.getInt(
            "DelayMs",
            "client.item.IconDumper",
            itemIconDelayMs,
            0,
            1000,
            "ItemIconDumper delay between items in milliseconds");
        itemIconSize = configuration.getInt(
            "iconSize",
            "client.item.IconDumper",
            itemIconSize,
            16,
            256,
            "ItemIconDumper output icon size in pixels");

        blockTileDelayMs = configuration.getInt(
            "DelayMs",
            "client.block.TileDumper",
            blockTileDelayMs,
            0,
            1000,
            "MapTileDumper delay between blocks in milliseconds");
        blockTileSize = configuration.getInt(
            "tileSize",
            "client.block.TileDumper",
            blockTileSize,
            16,
            256,
            "MapTileDumper output tile size in pixels");

        fluidIconDelayMs = configuration.getInt(
            "DelayMs",
            "client.fluid.IconDumper",
            fluidIconDelayMs,
            0,
            1000,
            "FluidIconDumper delay between fluids in milliseconds");
        fluidIconSize = configuration.getInt(
            "iconSize",
            "client.fluid.IconDumper",
            fluidIconSize,
            16,
            256,
            "FluidIconDumper output icon size in pixels");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
