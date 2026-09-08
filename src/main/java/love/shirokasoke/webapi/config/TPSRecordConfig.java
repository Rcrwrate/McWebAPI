package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.tpsRecorder", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class TPSRecordConfig {

    @Config.Comment("Enable TPS recording to file")
    public static boolean enable = true;

    @Config.Name("dimIds")
    @Config.Comment("Dimension IDs to record. Empty list = record all loaded worlds")
    public static int[] dimIds = new int[] { 0 };

    @Config.Name("interval")
    @Config.Comment("Recording interval in seconds")
    @Config.RangeInt(min = 1, max = 3600)
    public static int interval = 5;

    @Config.Name("file")
    @Config.Comment("Output file path for TPS records (CSV format)")
    public static String file = "dumps/tps_record.csv";
}
