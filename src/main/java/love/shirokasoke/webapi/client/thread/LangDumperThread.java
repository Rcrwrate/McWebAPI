package love.shirokasoke.webapi.client.thread;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StringTranslate;

import org.apache.commons.io.Charsets;

import love.shirokasoke.webapi.MyMod;

public class LangDumperThread extends Thread {

    /** 缓存 {@link StringTranslate#instance} 的私有字段 */
    private static Field INSTANCE_FIELD = null;
    /** 缓存 {@link StringTranslate#languageList} 的私有字段 */
    private static Field LANGUAGE_LIST_FIELD = null;

    private final Minecraft mc;

    public LangDumperThread() {
        super("Lang-Dumper");
        setDaemon(true);
        this.mc = Minecraft.getMinecraft();
    }

    @Override
    public void run() {
        // log.debugFields(new StringTranslate());
        try {
            Map<String, String> lang = getLanguageList();
            MyMod.LOG.info("[LangDumper] 获取到 {} 条翻译条目", lang.size());

            File langFile = new File(mc.mcDataDir, "dumps/export.lang");
            MyMod.LOG.info("[LangDumper] 开始导出 .lang 文件: {}", langFile.getAbsolutePath());
            int count = exportLangFile(langFile, lang);
            MyMod.LOG.info("[LangDumper] 导出完成，共 {} 条翻译，输出: {}", count, langFile.getAbsolutePath());
        } catch (Throwable e) {
            MyMod.LOG.error("[LangDumper] 导出语言文件时出错", e);
        }
    }

    private static StringTranslate getInstance()
        throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        if (INSTANCE_FIELD == null) {
            // field_74817_a是instance混淆后的字段名称
            INSTANCE_FIELD = StringTranslate.class.getDeclaredField("field_74817_a");
            INSTANCE_FIELD.setAccessible(true);
        }
        return (StringTranslate) INSTANCE_FIELD.get(null);
    }

    private static Map<String, String> getLanguageList() throws Exception {
        StringTranslate inst = getInstance();
        if (LANGUAGE_LIST_FIELD == null) {
            // field_74816_c是languageList混淆后的字段名称
            LANGUAGE_LIST_FIELD = StringTranslate.class.getDeclaredField("field_74816_c");
            LANGUAGE_LIST_FIELD.setAccessible(true);
        }
        synchronized (inst) {
            @SuppressWarnings("unchecked")
            Map<String, String> raw = (Map<String, String>) LANGUAGE_LIST_FIELD.get(inst);
            return new HashMap<>(raw);
        }
    }

    private static int exportLangFile(File outputFile, Map<String, String> lang) throws IOException {
        TreeMap<String, String> sorted = new TreeMap<>(lang);
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(outputFile), Charsets.UTF_8));
        try {
            int count = 0;
            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
                count++;
            }
            return count;
        } finally {
            writer.close();
        }
    }
}
