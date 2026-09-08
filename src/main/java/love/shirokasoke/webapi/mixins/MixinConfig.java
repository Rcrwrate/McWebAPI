package love.shirokasoke.webapi.mixins;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.config.Configs;

/**
 * Mixin switches. It owns the {@code mixin} category so that gtnhlib never sees an orphaned parent category.
 *
 * <p>
 * Registers itself on first access, because the mixin loaders run during the CoreMod phase - long before
 * {@link Configs#init()}. Every switch only takes effect after a restart.
 * </p>
 */
@Config(modid = MyMod.MODID, category = "mixin", filename = "WebAPI-mixins", configSubDirectory = "shirokasoke")
public class MixinConfig {

    public static final Early early;
    public static final NBT nbt;
    public static final Late late;

    static {
        early = new Early();
        nbt = new NBT();
        late = new Late();
        ConfigurationManager.registerConfig(MixinConfig.class);
    }

    public static class Early {

        @Config.Comment({
            "OversizedChunkMixin: print Hodgepodge's oversized chunk warning only once per offset, then suppress "
                + "repeats",
            "Requires Hodgepodge's 'remove2MBChunkLimit' to be enabled" })
        @Config.DefaultBoolean(true)
        public boolean enableOversizedChunkWarnOnce;
    }

    public static class NBT {

        @Config.Comment("Replace reflection on NBT data with direct field access")
        @Config.DefaultBoolean(true)
        public boolean enableNBT;
    }

    public static class Late {

        @Config.Comment("MTELapotronicSuperCapacitor.getInfoMap: fix GT5 LapotronicSuperCapacitor's getInfoMap method")
        @Config.DefaultBoolean(true)
        public boolean enableMTELapotronicSuperCapacitorGetInfoMap;

        @Config.Comment("AE2 crafting CPU internal state: replace reflection with Mixin accessors")
        @Config.DefaultBoolean(true)
        public boolean enableAECPUAccessor;

        @Config.Comment({ "ServerThreadLongHashMap.logOffThread: print a short message instead of the full warning",
            "for off-thread chunk reads initiated by WebAPI's own threads (Hodgepodge)" })
        @Config.DefaultBoolean(true)
        public boolean enableServerThreadLongHashMapBypass;
    }
}
