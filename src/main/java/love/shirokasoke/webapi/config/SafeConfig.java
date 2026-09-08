package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.safe", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class SafeConfig {

    @Config.Name("chunk")
    @Config.Comment("Enable chunk thread safe mode")
    public static boolean chunkSafe = true;
}
