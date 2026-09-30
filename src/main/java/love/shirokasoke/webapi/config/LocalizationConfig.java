package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "localization", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class LocalizationConfig {

    @Config.Comment({ "List of .lang files to inject into server localization (relative to classpath root)",
        "e.g. 'assets/minecraft/lang/zh_CN.lang', 'assets/forge/lang/zh_CN.lang'",
        "Route descriptions live in 'assets/webapi/lang/zh_CN.lang' (Chinese) and 'assets/webapi/lang/en_US.lang' (English);",
        "later entries override earlier ones for duplicated keys, so pick the language you want" })
    public static String[] langFiles = new String[] { "assets/minecraft/lang/zh_CN.lang", "dumps/export.lang",
        "assets/webapi/lang/zh_CN.lang" };
}
