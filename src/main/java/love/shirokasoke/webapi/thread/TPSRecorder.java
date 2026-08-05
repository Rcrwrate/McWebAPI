package love.shirokasoke.webapi.thread;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import cpw.mods.fml.common.FMLCommonHandler;
import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.Logs;

/**
 * TPS Recorder - Periodically samples server TPS / TickTime and appends records
 * to a CSV file. Records the real-world timestamp and the per-dimension TickTime
 * (in milliseconds) and TPS for the configured dimensions (or all loaded worlds
 * when the configured dim id list is empty).
 */
public class TPSRecorder extends Thread {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile TPSRecorder instance;

    private final File outputFile;
    private volatile boolean running = true;
    private BufferedWriter writer;
    private long sleepMs;
    private Set<Integer> targetDims;

    private TPSRecorder(File outputFile) {
        super("TPS-Recorder");
        setDaemon(true);
        this.outputFile = outputFile;
        this.sleepMs = Config.tpsRecordInterval * 1000L;
        if (this.sleepMs <= 0) this.sleepMs = 5000L;
        this.targetDims = resolveTargetDims();
    }

    public static void _start_() {
        if (!Config.tpsRecordEnable) return;
        if (instance != null && instance.isAlive()) return;
        instance = new TPSRecorder(new File(Config.tpsRecordFile));
        instance.start();
    }

    public static void _stop_() {
        TPSRecorder rec = instance;
        if (rec != null) {
            rec.shutdown();
            instance = null;
        }
    }

    public void shutdown() {
        this.running = false;
        this.interrupt();
    }

    @Override
    public void run() {
        try {
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile()
                    .mkdirs();
            }
            boolean needHeader = !outputFile.exists() || outputFile.length() == 0;
            writer = new BufferedWriter(new FileWriter(outputFile, true));
            if (needHeader) {
                writer.write("Timestamp,EpochMs,DimId,WorldName,TickTime,TPS");
                writer.newLine();
                writer.flush();
            }

            MyMod.LOG.info(
                "开始记录TPS数据，间隔 {} 秒，输出文件: {} ，目标维度: {}",
                Config.tpsRecordInterval,
                outputFile.getAbsolutePath(),
                targetDims.isEmpty() ? "全部" : targetDims.toString());

            while (running) {
                try {
                    recordOnce();
                } catch (Throwable e) {
                    MyMod.LOG.error("TPS record failed");
                    Logs.e(e);
                }

                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread()
                        .interrupt();
                    break;
                }
            }
        } catch (Throwable e) {
            MyMod.LOG.error("[TPSRecorder] 记录TPS数据时出错");
            Logs.e(e);
        } finally {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException ignored) {}
            }
            MyMod.LOG.info("[TPSRecorder] 已停止记录TPS数据");
        }
    }

    private void recordOnce() throws IOException {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) return;

        String timestamp = LocalDateTime.now()
            .format(DATE_FORMAT);
        long epochMs = System.currentTimeMillis();

        for (Integer dimId : DimensionManager.getIDs()) {
            if (!targetDims.isEmpty() && !targetDims.contains(dimId)) continue;

            long[] tickTimes = server.worldTickTimes.get(dimId);
            if (tickTimes == null || tickTimes.length == 0) continue;

            double worldTickTime = mean(tickTimes) * 1.0E-6D;
            double worldTPS = Math.min(1000.0 / worldTickTime, 20);

            String worldName = "";
            WorldServer world = DimensionManager.getWorld(dimId.intValue());
            if (world != null && world.provider != null) {
                worldName = world.provider.getDimensionName();
            }

            writer.write(timestamp);
            writer.write(",");
            writer.write(String.valueOf(epochMs));
            writer.write(",");
            writer.write(dimId.toString());
            writer.write(",");
            writer.write(escape(worldName));
            writer.write(",");
            writer.write(String.format("%.4f", worldTickTime));
            writer.write(",");
            writer.write(String.format("%.2f", worldTPS));
            writer.newLine();
        }
        writer.flush();
    }

    private static Set<Integer> resolveTargetDims() {
        Set<Integer> set = new HashSet<>();
        if (Config.tpsRecordDimIds != null) {
            for (int id : Config.tpsRecordDimIds) {
                set.add(id);
            }
        }
        return set;
    }

    private static long mean(long[] values) {
        long sum = 0L;
        for (long v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private static String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
