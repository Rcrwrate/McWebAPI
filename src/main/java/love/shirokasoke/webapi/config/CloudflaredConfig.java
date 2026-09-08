package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.cf", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class CloudflaredConfig {

    @Config.Name("path")
    @Config.Comment({ "Path to the cloudflared binary file (empty = tunnel disabled)",
        "If missing, it is auto-downloaded into this exact path" })
    public static String path = "";

    @Config.Name("token")
    @Config.Comment("Cloudflare Tunnel token (empty = quick tunnel, requires no account)")
    public static String token = "";
}
