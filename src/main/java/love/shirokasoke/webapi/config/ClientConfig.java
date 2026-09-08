package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "client", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class ClientConfig {

    public static final Item item = new Item();

    public static final Block block = new Block();

    public static final Fluid fluid = new Fluid();

    public static class Item {

        @Config.Name("DelayMs")
        @Config.Comment("ItemIconDumper delay between items in milliseconds")
        @Config.RangeInt(min = 0, max = 1000)
        public int delayMs = 10;

        @Config.Name("iconSize")
        @Config.Comment("ItemIconDumper output icon size in pixels")
        @Config.RangeInt(min = 16, max = 256)
        public int iconSize = 256;
    }

    public static class Block {

        @Config.Name("DelayMs")
        @Config.Comment("MapTileDumper delay between blocks in milliseconds")
        @Config.RangeInt(min = 0, max = 1000)
        public int delayMs = 10;

        @Config.Name("tileSize")
        @Config.Comment("MapTileDumper output tile size in pixels")
        @Config.RangeInt(min = 16, max = 256)
        public int tileSize = 64;
    }

    public static class Fluid {

        @Config.Name("DelayMs")
        @Config.Comment("FluidIconDumper delay between fluids in milliseconds")
        @Config.RangeInt(min = 0, max = 1000)
        public int delayMs = 10;

        @Config.Name("iconSize")
        @Config.Comment("FluidIconDumper output icon size in pixels")
        @Config.RangeInt(min = 16, max = 256)
        public int iconSize = 256;
    }
}
