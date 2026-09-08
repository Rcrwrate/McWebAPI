package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.ae2", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class AE2Config {

    public static final Item item = new Item();

    public static class Item {

        @Config.Name("Interval")
        @Config.Comment("AE2 item cache refresh interval in seconds")
        @Config.RangeInt(min = 1, max = 3600)
        public int interval = 5;

        @Config.Name("IdleTimeout")
        @Config.Comment("AE2 item cache idle timeout in minutes (cache is dropped if no access within this period)")
        @Config.RangeInt(min = 1, max = 1440)
        public int idleTimeout = 30;
    }
}
