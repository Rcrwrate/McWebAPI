package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "client.fluid", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class ClientFluidConfig {

    @Config.Name("DelayMs")
    @Config.Comment("FluidIconDumper delay between fluids in milliseconds")
    @Config.RangeInt(min = 0, max = 1000)
    public static int delayMs = 10;

    @Config.Name("iconSize")
    @Config.Comment("FluidIconDumper output icon size in pixels")
    @Config.RangeInt(min = 16, max = 256)
    public static int iconSize = 256;
}
