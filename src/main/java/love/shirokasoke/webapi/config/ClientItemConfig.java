package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "client.item", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class ClientItemConfig {

    @Config.Name("DelayMs")
    @Config.Comment("ItemIconDumper delay between items in milliseconds")
    @Config.RangeInt(min = 0, max = 1000)
    public static int delayMs = 10;

    @Config.Name("iconSize")
    @Config.Comment("ItemIconDumper output icon size in pixels")
    @Config.RangeInt(min = 16, max = 256)
    public static int iconSize = 256;
}
