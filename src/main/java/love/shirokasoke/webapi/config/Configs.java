package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import love.shirokasoke.webapi.MyMod;

public final class Configs {

    private Configs() {}

    public static void init() {
        ConfigurationManager.registerConfig(ServerConfig.class);

        ConfigurationManager.registerConfig(SecurityConfig.class);
        ConfigurationManager.registerConfig(StaticResourceConfig.class);
        ConfigurationManager.registerConfig(ItemThreadConfig.class);
        ConfigurationManager.registerConfig(AE2Config.class);
        ConfigurationManager.registerConfig(RecipeConfig.class);
        ConfigurationManager.registerConfig(TickConfig.class);
        ConfigurationManager.registerConfig(TPSRecordConfig.class);
        ConfigurationManager.registerConfig(CompressorConfig.class);
        ConfigurationManager.registerConfig(OCConfig.class);
        ConfigurationManager.registerConfig(CloudflaredConfig.class);
        ConfigurationManager.registerConfig(SafeConfig.class);

        ConfigurationManager.registerConfig(DebugConfig.class);
        ConfigurationManager.registerConfig(UpdateConfig.class);
        ConfigurationManager.registerConfig(LocalizationConfig.class);

        ConfigurationManager.registerConfig(ClientConfig.class);

        MyMod.LOG.info("Configuration loaded");
    }
}
