package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import love.shirokasoke.webapi.MyMod;

/**
 * Central registration point of every WebAPI config class.
 *
 * <p>
 * Every config class writes into the same file ({@code config/shirokasoke/WebAPI.cfg}) under its own category, so the
 * historical category layout is kept. Call {@link #init()} during preInit.
 * </p>
 */
public final class Configs {

    private Configs() {}

    public static void init() {
        ConfigurationManager.registerConfig(ServerConfig.class);

        ConfigurationManager.registerConfig(SecurityConfig.class);
        ConfigurationManager.registerConfig(StaticResourceConfig.class);
        ConfigurationManager.registerConfig(ItemThreadConfig.class);
        ConfigurationManager.registerConfig(AE2ItemConfig.class);
        ConfigurationManager.registerConfig(RecipeConfig.class);
        ConfigurationManager.registerConfig(TickConfig.class);
        ConfigurationManager.registerConfig(TPSRecordConfig.class);
        ConfigurationManager.registerConfig(CompressorConfig.class);
        ConfigurationManager.registerConfig(OCPrintConfig.class);
        ConfigurationManager.registerConfig(CloudflaredConfig.class);
        ConfigurationManager.registerConfig(SafeConfig.class);

        ConfigurationManager.registerConfig(DebugConfig.class);
        ConfigurationManager.registerConfig(UpdateConfig.class);
        ConfigurationManager.registerConfig(LocalizationConfig.class);

        ConfigurationManager.registerConfig(ClientItemConfig.class);
        ConfigurationManager.registerConfig(ClientBlockConfig.class);
        ConfigurationManager.registerConfig(ClientFluidConfig.class);

        MyMod.LOG.info("Configuration loaded");
    }
}
