package love.shirokasoke.webapi.mixins;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.config.Configs;

/**
 * Early mixin switches, i.e. mixins targeting vanilla/forge classes.
 *
 * <p>
 * Registers itself on first access, because early mixins are resolved during the CoreMod phase - long before
 * {@link Configs#init()} runs. Every mixin switch only takes effect after a restart.
 * </p>
 */
@Config(modid = MyMod.MODID, category = "mixin.early", filename = "WebAPI-mixins", configSubDirectory = "shirokasoke")
public class EarlyMixinConfig {

    static {
        ConfigurationManager.registerConfig(EarlyMixinConfig.class);
    }

    @Config.Comment({
        "OversizedChunkMixin: print Hodgepodge's oversized chunk warning only once per offset, then suppress repeats",
        "Requires Hodgepodge's 'remove2MBChunkLimit' to be enabled" })
    @Config.DefaultBoolean(true)
    public static boolean enableOversizedChunkWarnOnce;

    @Config.Comment("Replace reflection on NBT data with direct field access")
    @Config.DefaultBoolean(true)
    public static boolean enableNBT = true;
}
