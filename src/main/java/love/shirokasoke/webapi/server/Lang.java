package love.shirokasoke.webapi.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import net.minecraft.util.StringTranslate;

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
    }
}
