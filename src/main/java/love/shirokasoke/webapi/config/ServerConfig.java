package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class ServerConfig {

    @Config.Comment("HTTP server port")
    @Config.RangeInt(min = 1024, max = 65535)
    public static int httpPort = 40002;

    @Config.Comment("WebServer threads (ignored when useVirtualThreads is enabled)")
    @Config.RangeInt(min = 4, max = 36)
    public static int nThreads = 10;

    @Config.Comment("Use virtual threads (default requires Java 21+, but here need Java 25+)")
    public static boolean useVirtualThreads = true;

    @Config.Comment("Use SSE instead of polling (requires virtual threads; the payload cannot be compressed, so it costs more bandwidth)")
    public static boolean useServerSideEvent = true;

    @Config.Comment("Experimental: register /ae/cpu/task/background, which computes the crafting tree on the web thread instead of the server tick (submitJob still runs on the server thread)")
    public static boolean experimentalBackgroundCrafting = false;
}
