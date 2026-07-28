package love.shirokasoke.webapi.mixins;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

// The annotation is required, it indicates to
// the mixins framework to instantiate this class
// and look for LateMixins to load.
@LateMixin
public class LateMixinsLoader implements ILateMixinLoader {

    private static final Logger LOG = LogManager.getLogger("WebAPI-LateMixins");

    @Override
    public String getMixinConfig() {
        return "mixins.webapi.late.json";
    }

    @Nonnull
    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        MixinConfig.load();
        // Register your late mixins here by adding them to the list.
        // The late mixins target classes from other mods.
        // The loadedMods contains the mod ID of currently loaded mods,
        // you can check this Set to conditionally load certain mixins.
        List<String> mixins = new ArrayList<>();

        // Example: conditionally enable a late mixin based on config + mod presence:
        // if (MixinConfig.enableSomeModMixin && loadedMods.contains("some_modid")) {
        // mixins.add("SomeModMixin");
        // }
        if (MixinConfig.enableMTELapotronicSuperCapacitorGetInfoMap && loadedMods.contains("gregtech")) {
            mixins.add("MTELapotronicSuperCapacitorGetInfoMapMixin");
        }

        if (MixinConfig.enableNBT) {
            mixins.add("NBTMixin");
        }

        if (MixinConfig.ServerThreadLongHashMapBypass) {
            mixins.add("ServerThreadLongHashMapBypass");
        }

        if (!mixins.isEmpty()) {
            LOG.info("Enabled late mixins: {}", mixins);
        }

        return mixins;
    }
}
