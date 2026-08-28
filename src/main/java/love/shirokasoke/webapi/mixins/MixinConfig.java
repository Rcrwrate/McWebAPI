package love.shirokasoke.webapi.mixins;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import net.minecraft.launchwrapper.Launch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mixin 专用的配置文件读取器。
 * <p>
 * 配置文件位置: {@code config/shirokasoke/webapi-mixins.properties}
 */
public final class MixinConfig {

    private static final Logger LOG = LogManager.getLogger("WebAPI-Mixin");
    private static final String CONFIG_FILENAME = "webapi-mixins.properties";

    private static boolean loaded = false;

    // ========== Early Mixin 开关 (操作 vanilla/forge 类) ==========

    /** OversizedChunkMixin: Hodgepodge 超大区块写入警告每个 offset 仅提示一次，之后拦截 */
    public static boolean enableOversizedChunkWarnOnce = true;

    // ========== Late Mixin 开关 (操作其他 mod 的类) ==========

    /** MTELapotronicSuperCapacitor.getInfoMap: 修复 GT5 LapotronicSuperCapacitor 的 getInfoMap 方法 */
    public static boolean enableMTELapotronicSuperCapacitorGetInfoMap = true;

    public static boolean enableNBT = true;

    /** AE2 合成 CPU 内部状态：使用 Mixin 替代反射 */
    public static boolean enableAECPUAccessor = true;

    /** ServerThreadLongHashMap.logOffThread: 本 mod 线程的 off-thread 读取仅打印简短信息 (Hodgepodge) */
    public static boolean ServerThreadLongHashMapBypass = true;

    private MixinConfig() {}

    /**
     * 加载配置文件。如果文件不存在则从 jar 内嵌默认配置创建。
     * 此方法可安全多次调用（幂等）。
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }

        File configFile = getConfigFile();
        ensureDefaultConfig(configFile);

        Properties props = new Properties();
        try (InputStream is = new FileInputStream(configFile)) {
            props.load(is);
        } catch (IOException e) {
            LOG.warn("Failed to load mixin config {}, using defaults: {}", configFile.getPath(), e.getMessage());
        }

        // 读取各开关值
        enableOversizedChunkWarnOnce = parseBoolean(
            props,
            "mixin.early.OversizedChunkWarnOnce",
            enableOversizedChunkWarnOnce);
        enableMTELapotronicSuperCapacitorGetInfoMap = parseBoolean(
            props,
            "mixin.late.MTELapotronicSuperCapacitor.getInfoMap",
            enableMTELapotronicSuperCapacitorGetInfoMap);
        enableNBT = parseBoolean(props, "mixin.nbt", enableNBT);
        enableAECPUAccessor = parseBoolean(props, "mixin.late.AECPUAccessor", enableAECPUAccessor);
        ServerThreadLongHashMapBypass = parseBoolean(
            props,
            "mixin.late.ServerThreadLongHashMapBypass",
            ServerThreadLongHashMapBypass);

        logCurrentState();
        loaded = true;
    }

    // ==================== 内部方法 ====================

    private static File getConfigFile() {
        return new File(new File(new File(Launch.minecraftHome, "config"), "shirokasoke"), CONFIG_FILENAME);
    }

    /**
     * 如果配置文件不存在，从 jar 内嵌的默认配置文件复制生成。
     * 默认配置路径: assets/webapi/default_config/{CONFIG_FILENAME}
     */
    private static void ensureDefaultConfig(File target) {
        if (target.exists()) {
            return;
        }

        String resourcePath = "assets/webapi/default_config/" + CONFIG_FILENAME;
        InputStream resourceStream = Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream(resourcePath);

        if (resourceStream == null) {
            LOG.warn("Embedded default config not found at {}, creating minimal default", resourcePath);
            createMinimalDefault(target);
            return;
        }

        target.getParentFile()
            .mkdirs();
        try (OutputStream os = new FileOutputStream(target)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = resourceStream.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            LOG.info("Generated default mixin config: {}", target.getAbsolutePath());
        } catch (IOException e) {
            LOG.error("Failed to create default config file: {}", e.getMessage());
        }
    }

    /**
     * 内嵌默认资源缺失时的兜底：直接写一个最小化默认配置。
     */
    private static void createMinimalDefault(File target) {
        target.getParentFile()
            .mkdirs();
        Properties defaultProps = new Properties();
        defaultProps.setProperty("mixin.early.OversizedChunkWarnOnce", String.valueOf(enableOversizedChunkWarnOnce));
        defaultProps.setProperty(
            "mixin.late.MTELapotronicSuperCapacitor.getInfoMap",
            String.valueOf(enableMTELapotronicSuperCapacitorGetInfoMap));
        defaultProps.setProperty("mixin.nbt", String.valueOf(enableNBT));
        defaultProps.setProperty("mixin.late.AECPUAccessor", String.valueOf(enableAECPUAccessor));
        defaultProps
            .setProperty("mixin.late.ServerThreadLongHashMapBypass", String.valueOf(ServerThreadLongHashMapBypass));

        try (OutputStream os = new FileOutputStream(target)) {
            defaultProps.store(
                os,
                "WebAPI Mixin Configuration\n"
                    + "Set each value to true/false to control whether the corresponding mixin is loaded.\n"
                    + "After changes, restart the game for them to take effect.");
        } catch (IOException e) {
            LOG.error("Failed to write minimal default config: {}", e.getMessage());
        }
    }

    private static boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        String val = props.getProperty(key);
        if (val == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(val.trim());
    }

    private static void logCurrentState() {
        LOG.info("Mixin config loaded:");
        LOG.info("  mixin.early.OversizedChunkWarnOnce = {}", enableOversizedChunkWarnOnce);
        LOG.info(
            "  mixin.late.MTELapotronicSuperCapacitor.getInfoMap = {}",
            enableMTELapotronicSuperCapacitorGetInfoMap);
        LOG.info("  mixin.nbt = {}", enableNBT);
        LOG.info("  mixin.late.AECPUAccessor = {}", enableAECPUAccessor);
        LOG.info("  mixin.late.ServerThreadLongHashMapBypass = {}", ServerThreadLongHashMapBypass);
    }
}
