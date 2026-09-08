package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.ae2.item", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class AE2ItemConfig {

    @Config.Name("Interval")
    @Config.Comment("AE2 item cache refresh interval in seconds")
    @Config.RangeInt(min = 1, max = 3600)
    public static int interval = 5;

    @Config.Name("IdleTimeout")
    @Config.Comment("AE2 item cache idle timeout in minutes (cache is dropped if no access within this period)")
    @Config.RangeInt(min = 1, max = 1440)
    public static int idleTimeout = 30;
}
