package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "update", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class UpdateConfig {

    @Config.Comment("Enable update checking on server start")
    public static boolean enableUpdateCheck = true;
}
