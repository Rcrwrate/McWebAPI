package love.shirokasoke.webapi.mixins;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.config.Configs;

/**
 * Late mixin switches, i.e. mixins targeting classes of other mods.
 *
 * <p>
 * Registers itself on first access, because the late mixin loader may run before {@link Configs#init()}. Every mixin
 * switch only takes effect after a restart.
 * </p>
 */
@Config(modid = MyMod.MODID, category = "mixin.late", filename = "WebAPI-mixins", configSubDirectory = "shirokasoke")
public class LateMixinConfig {

    static {
        ConfigurationManager.registerConfig(LateMixinConfig.class);
    }

    @Config.Comment("MTELapotronicSuperCapacitor.getInfoMap: fix GT5 LapotronicSuperCapacitor's getInfoMap method")
    @Config.DefaultBoolean(true)
    public static boolean enableMTELapotronicSuperCapacitorGetInfoMap;

    @Config.Comment("AE2 crafting CPU internal state: replace reflection with Mixin accessors")
    @Config.DefaultBoolean(true)
    public static boolean enableAECPUAccessor;

    @Config.Comment({ "ServerThreadLongHashMap.logOffThread: print a short message instead of the full warning",
        "for off-thread chunk reads initiated by WebAPI's own threads (Hodgepodge)" })
    @Config.DefaultBoolean(true)
    public static boolean enableServerThreadLongHashMapBypass;
}
