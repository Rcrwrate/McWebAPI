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
}
