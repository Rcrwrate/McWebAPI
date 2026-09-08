package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "localization", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class LocalizationConfig {

    @Config.Comment({ "List of .lang files to inject into server localization (relative to classpath root)",
        "e.g. 'assets/minecraft/lang/zh_CN.lang', 'assets/forge/lang/zh_CN.lang'" })
    public static String[] langFiles = new String[] { "assets/minecraft/lang/zh_CN.lang", "dumps/export.lang" };
}
