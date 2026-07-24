package love.shirokasoke.webapi.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.util.StatCollector;
import net.minecraft.util.StringTranslate;

import gregtech.api.enums.Materials;
import love.shirokasoke.webapi.MyMod;

public class Lang {

    public static void setup(String[] load) {
        for (String path : load) {
            if (path == null || path.trim()
                .isEmpty()) continue;
            path = path.trim();

            InputStream langStream = null;

            try {
                langStream = Lang.class.getResourceAsStream("/" + path);
            } catch (Exception ignored) {}

            if (langStream == null) {
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    try {
                        langStream = new FileInputStream(file);
                    } catch (Exception _ignored) {}
                }
            }

            if (langStream != null) {
                try {
                    StringTranslate.inject(langStream);
                    MyMod.LOG.info("Injected lang file: " + path);
                } catch (Exception e) {
                    MyMod.LOG.warn("Failed to inject lang file: " + path, e);
                } finally {
                    try {
                        langStream.close();
                    } catch (Exception _ignored) {}
                }
            } else {
                MyMod.LOG.error("Lang file not found: " + path);
            }
        }

        // GT5 在 preInit 阶段把每个 Material 的本地化名冻结进 mLocalizedName 缓存字段，
        // 此后所有 %material 占位符都走该缓存，而非动态查询翻译表。
        // 这里在 inject 完成后强制刷新该缓存。
        // refreshGTMaterialLocalizedNames();
        // 2.9.0似乎移除了lang缓存，等待验证
    }

    /**
     * 刷新 GregTech {@link Materials#mLocalizedName} 缓存字段，使其读取最新注入的翻译。
     */
    @SuppressWarnings("unused")
    private static void refreshGTMaterialLocalizedNames() {
        try {
            Method getMaterialsMap = Materials.class.getMethod("getMaterialsMap");
            @SuppressWarnings("unchecked")
            Map<String, ?> materialsMap = (Map<String, ?>) getMaterialsMap.invoke(null);

            Field fName = Materials.class.getField("mName");
            Field fDefaultLocalName = Materials.class.getField("mDefaultLocalName");
            Field fLocalizedName = Materials.class.getField("mLocalizedName");

            int refreshed = 0;
            for (Object material : materialsMap.values()) {
                if (material == null) continue;
                String mName = (String) fName.get(material);
                if (mName == null || mName.isEmpty() || "null".equals(mName)) continue;

                String key = "Material." + mName.toLowerCase();
                String translated;
                if (StatCollector.canTranslate(key)) {
                    translated = StatCollector.translateToLocal(key);
                } else {
                    // 翻译表里没有时，回退到材料默认名，避免残留旧缓存
                    translated = (String) fDefaultLocalName.get(material);
                }
                fLocalizedName.set(material, translated);
                refreshed++;
            }
            MyMod.LOG.info("Refreshed " + refreshed + " GregTech Material localized names");
        } catch (Throwable t) {
            MyMod.LOG.warn("Failed to refresh GregTech Material localized names", t);
        }
    }
}
