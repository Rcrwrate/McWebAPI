package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "client.block", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class ClientBlockConfig {

    @Config.Name("DelayMs")
    @Config.Comment("MapTileDumper delay between blocks in milliseconds")
    @Config.RangeInt(min = 0, max = 1000)
    public static int delayMs = 10;

    @Config.Name("tileSize")
    @Config.Comment("MapTileDumper output tile size in pixels")
    @Config.RangeInt(min = 16, max = 256)
    public static int tileSize = 64;
}
