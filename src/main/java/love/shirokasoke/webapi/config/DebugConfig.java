package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "debug", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class DebugConfig {

    @Config.Comment("Allow class dump")
    public static boolean classDump = false;

    @Config.Comment("Allow test route register")
    public static boolean test = false;
}
