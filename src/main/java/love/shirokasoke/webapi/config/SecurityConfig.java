package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.security", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class SecurityConfig {

    @Config.Comment("Simple token used to auth HTTP requests")
    public static String authToken = "";

    @Config.Comment({ "List of URL prefixes which need auth",
        "Format: '<path>|GET|POST', e.g. '/setblock|GET|POST', '/chunk/force|GET|POST'" })
    public static String[] authUrlPrefixes = new String[] { "/setblock|GET|POST", "/chunk/force|GET|POST" };

    @Config.Comment({ "List of URL prefixes to ban",
        "Format: '<path>|GET|POST', e.g. '/setblock|GET|POST', '/chunk/force|GET|POST'" })
    public static String[] bannedPrefixes = new String[] {};

    @Config.Comment("List of route paths to disable completely (e.g. '/test', '/profiler')")
    public static String[] disabledRoutes = new String[] {};
}
