package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.static", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class StaticResourceConfig {

    @Config.Name("ItemFile")
    @Config.Comment("If ItemFile is set and valid, itemThread will be disabled forcibly")
    public static String itemFile = "dumps/items.json";

    @Config.Name("ItemIconFolder")
    @Config.Comment("Client dumped item icon folder")
    public static String itemIconFolder = "dumps/item_icons";

    @Config.Name("BlockFile")
    @Config.Comment("If BlockFile is set and valid, block data will be loaded from this file")
    public static String blockFile = "dumps/blocks.json";

    @Config.Name("BlockTileFolder")
    @Config.Comment("Client dumped block tile folder")
    public static String blockTileFolder = "dumps/block_tiles";

    @Config.Name("FluidIconFolder")
    @Config.Comment("Client dumped fluid icon folder")
    public static String fluidIconFolder = "dumps/fluid_icons";
}
