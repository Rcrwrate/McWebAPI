package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.itemThread", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class ItemThreadConfig {

    @Config.Comment("Enable item cache auto build")
    public static boolean enable = false;

    @Config.Name("DelayMs")
    @Config.Comment("ItemsThread processing delay in milliseconds (0 to disable, higher = slower but less memory pressure)")
    @Config.RangeInt(min = 0, max = 1000)
    public static int delayMs = 10;

    @Config.Name("BatchSize")
    @Config.Comment("ItemsThread batch size for GC hints (items processed before System.gc() call)")
    @Config.RangeInt(min = 10, max = 1000)
    public static int batchSize = 100;
}
