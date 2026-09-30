package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.tick", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class TickConfig {

    @Config.Name("MaxPerTick")
    @Config.Comment("Max count of slow tasks that can be run per tick")
    @Config.RangeInt(min = 1, max = 10000)
    public static int maxPerTick = 10000;

    @Config.Name("BudgetMs")
    @Config.Comment("Max time in ms for background tasks (pausable + slow queue) execution per tick")
    @Config.RangeInt(min = 1, max = 50)
    public static int budgetMs = 50;

    @Config.Name("DynamicBudgetEnable")
    @Config.Comment("Enable the dynamic budget task: periodically shrink the per-tick budget by the measured world tick time")
    public static boolean dynamicBudget = true;

    @Config.Name("DynamicBudgetInterval")
    @Config.Comment("Sampling interval of the dynamic budget task in seconds")
    @Config.RangeInt(min = 1, max = 3600)
    public static int dynamicBudgetInterval = 10;

    @Config.Name("DynamicBudgetDimIds")
    @Config.Comment("Dimension IDs charged to the dynamic budget. Empty list = charge all loaded worlds")
    public static int[] dynamicBudgetDimIds = new int[0];

    @Config.Name("DynamicBudgetSmoothing")
    @Config.Comment("Exponential smoothing: weight of the newest sample (0.0-1.0). Higher = reacts faster, lower = more stable")
    @Config.RangeFloat(min = 0.01F, max = 1.0F)
    public static float dynamicBudgetSmoothing = 0.3F;
}
