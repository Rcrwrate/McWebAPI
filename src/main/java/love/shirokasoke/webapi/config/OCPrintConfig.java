package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.oc.print", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class OCPrintConfig {

    @Config.Name("ALPHA_THRESHOLD")
    @Config.Comment("Alpha threshold for 3D print pixel opacity (pixels with alpha <= this are treated as transparent)")
    @Config.RangeInt(min = 0, max = 255)
    public static int alphaThreshold = 220;

    @Config.Name("TOLERANCE_R")
    @Config.Comment("Red channel tolerance for merging adjacent pixels into one 3D print shape")
    @Config.RangeInt(min = 0, max = 255)
    public static int toleranceR = 3;

    @Config.Name("TOLERANCE_G")
    @Config.Comment("Green channel tolerance for merging adjacent pixels into one 3D print shape")
    @Config.RangeInt(min = 0, max = 255)
    public static int toleranceG = 2;

    @Config.Name("TOLERANCE_B")
    @Config.Comment("Blue channel tolerance for merging adjacent pixels into one 3D print shape")
    @Config.RangeInt(min = 0, max = 255)
    public static int toleranceB = 5;
}
