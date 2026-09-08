package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.compressor", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class CompressorConfig {

    @Config.Comment("Enable web server adaptive compression")
    public static boolean enable = true;

    @Config.Name("threshold")
    @Config.Comment("Byte threshold for web server adaptive compression")
    @Config.RangeInt(min = 0)
    public static int threshold = 102400;
}
