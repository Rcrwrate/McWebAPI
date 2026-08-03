package love.shirokasoke.webapi.thread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.log;

public class CloudflaredTunnel {

    private static final String LOG_FILE = "logs" + File.separator + "cloudflared.log";
    private static final String RELEASE_BASE = "https://github.com/cloudflare/cloudflared/releases/latest/download/";
    private static volatile Process process;
    private static volatile boolean stopping;
    private static volatile Thread downloaderThread;

    private CloudflaredTunnel() {}

    /** Starts the tunnel process (no-op if cloudflared is already running). */
    public static synchronized void start() {
        // A new server session: allow the tunnel to be started again after a stop.
        stopping = false;
        if (Config.cfPath == null || Config.cfPath.trim()
            .isEmpty()) {
            MyMod.LOG.info("[CloudFlared] server.cf.path is empty, tunnel disabled");
            return;
        }
        if (process != null && process.isAlive()) {
            MyMod.LOG.warn("[CloudFlared] is already running");
            return;
        }
        if (downloaderThread != null && downloaderThread.isAlive()) {
            MyMod.LOG.info("[CloudFlared] binary download already in progress");
            return;
        }

        // Fast path: the binary already exists at the configured path. No network I/O.
        File binary = configuredBinary();
        if (binary != null) {
            launch(binary);
            return;
        }

        MyMod.LOG.info("[CloudFlared] binary not found at {}, downloading in background", Config.cfPath);
        downloaderThread = new Thread(CloudflaredTunnel::downloadAndStart, "CloudflaredTunnel-Downloader");
        downloaderThread.setDaemon(true);
        downloaderThread.start();
    }

    /** Stops the tunnel process (and its children) if running. */
    public static synchronized void stop() {
        stopping = true;
        if (downloaderThread != null && downloaderThread.isAlive()) {
            MyMod.LOG.info("[CloudFlared] download in progress, it will not launch after completion");
        }
        if (process == null) {
            return;
        }
        Process p = process;
        process = null;
        try {
            p.destroy();
        } catch (Exception ignored) {}
        try {
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            p.destroyForcibly();
        }
        MyMod.LOG.info("[CloudFlared] stopped");
    }

    /** Background task: download (if needed), then launch the tunnel. */
    private static void downloadAndStart() {
        File binary = ensureBinary();
        if (binary == null) {
            MyMod.LOG.error("[CloudFlared] unable to obtain a binary, tunnel NOT started");
            return;
        }
        synchronized (CloudflaredTunnel.class) {
            if (stopping) {
                MyMod.LOG.info("[CloudFlared] tunnel is stopping, downloaded binary will not be launched");
                return;
            }
            launch(binary);
        }
    }

    /** Launches the cloudflared process. Must be called while holding the class lock. */
    private static void launch(File binary) {
        try {
            ProcessBuilder builder = new ProcessBuilder(buildCommand(binary));
            builder.redirectErrorStream(true);
            builder.redirectOutput(new File(LOG_FILE));
            process = builder.start();
            MyMod.LOG.info("[CloudFlared] started, log -> {}", new File(LOG_FILE).getAbsolutePath());
        } catch (IOException e) {
            MyMod.LOG.error("[CloudFlared] failed to start cloudflared: {}", e.getMessage());
            log.e(e);
        }
    }

    /**
     * Fast path: returns the configured binary if it already exists at the
     * configured path, without performing any network I/O. Never blocks the caller.
     *
     * @return the existing cloudflared binary at {@link Config#cfPath}, or
     *         {@code null} if the file does not exist (yet).
     */
    private static File configuredBinary() {
        String configured = Config.cfPath.trim();
        File binary = new File(configured);
        if (binary.isFile()) {
            MyMod.LOG.debug("[CloudFlared] using configured binary: {}", binary.getAbsolutePath());
            return binary;
        }
        return null;
    }

    /**
     * Ensures the cloudflared binary is available at {@link Config#cfPath},
     * downloading / extracting it if necessary. Performs blocking network I/O
     * and therefore MUST be called on a background thread, never on the main
     * thread. The configured path is treated as the exact binary file location.
     *
     * @return the cloudflared binary to use, or {@code null} if unavailable.
     */
    private static File ensureBinary() {
        File binary = configuredBinary();
        if (binary != null) {
            return binary;
        }

        String os = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "")
            .toLowerCase(Locale.ROOT);
        String asset = resolveAsset(os, arch);
        if (asset == null) {
            MyMod.LOG.error(
                "[CloudFlared] unsupported platform: os={} arch={}, please set server.cf.path manually",
                os,
                arch);
            return null;
        }

        File binaryTarget = new File(Config.cfPath.trim()).getAbsoluteFile();
        File parent = binaryTarget.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            MyMod.LOG.error("[CloudFlared] cannot create directory {}", parent.getAbsolutePath());
            return null;
        }

        // On macOS the GitHub asset is a .tgz that must be unpacked first. The
        // archive is downloaded next to the target binary and removed afterwards.
        boolean compressed = asset.endsWith(".tgz");
        File download = new File(binaryTarget.getParentFile(), asset);
        String url = RELEASE_BASE + asset;
        try {
            MyMod.LOG.info("downloading {} ...", url);
            download(url, download);
            if (compressed) {
                extractTgz(download, binaryTarget);
            } else {
                if (download.renameTo(binaryTarget)) {
                    download = binaryTarget;
                } else if (!download.exists()) {
                    throw new IOException("rename failed: " + download.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            log.e(e);
            if (download.exists()) {
                download.delete();
            }
            if (binaryTarget.exists()) {
                binaryTarget.delete();
            }
            return null;
        }

        if (!binaryTarget.setExecutable(true, true)) {
            MyMod.LOG.warn("[CloudFlared] failed to set executable bit on {}", binaryTarget.getAbsolutePath());
        }
        MyMod.LOG.debug("[CloudFlared] binary ready: {}", binaryTarget.getAbsolutePath());
        return binaryTarget;
    }

    /**
     * Maps the current platform to a cloudflared GitHub release asset name.
     * Returns {@code null} for unsupported platforms.
     */
    private static String resolveAsset(String os, String arch) {
        String a;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            a = "arm64";
        } else if (arch.contains("arm")) {
            a = "arm";
        } else if (arch.contains("64") || arch.contains("x86_64")) {
            a = "amd64";
        } else {
            return null;
        }
        if (os.contains("win")) {
            return "cloudflared-windows-" + a + ".exe";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "cloudflared-darwin-" + (a.equals("amd64") ? "amd64" : "arm64") + ".tgz";
        }
        if (os.contains("linux")) {
            return "cloudflared-linux-" + a;
        }
        return null;
    }

    /** Builds the cloudflared command line. */
    private static List<String> buildCommand(File binary) {
        String token = Config.cfToken == null ? "" : Config.cfToken.trim();
        String url = "http://localhost:" + Config.httpPort;
        if (!token.isEmpty()) {
            MyMod.LOG.info("[CloudFlared] starting named tunnel with token (port {})", Config.httpPort);
            return Arrays.asList(binary.getAbsolutePath(), "tunnel", "run", "--token", token, "--url", url);
        }
        MyMod.LOG.info("[CloudFlared] starting trycloudflare quick tunnel for port {}", Config.httpPort);
        return Arrays.asList(binary.getAbsolutePath(), "tunnel", "--url", url);
    }

    private static void download(String url, File target) throws IOException {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            URL u = URI.create(url)
                .toURL();
            conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "WebAPI-CloudflaredTunnel");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " for " + url);
            }
            in = conn.getInputStream();
            out = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            out.flush();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {}
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Extracts a cloudflared .tgz (macOS asset) into {@code target} using the system tar. */
    private static void extractTgz(File tgz, File target) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "tar",
            "-xzf",
            tgz.getAbsolutePath(),
            "-C",
            tgz.getParentFile()
                .getAbsolutePath());
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new IOException("cannot run tar to extract " + tgz.getAbsolutePath(), e);
        }
        try {
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("tar extraction timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            p.destroyForcibly();
            throw new IOException("tar extraction interrupted", e);
        }
        if (p.exitValue() != 0) {
            throw new IOException("tar extraction failed with exit code " + p.exitValue());
        }
        File extracted = new File(tgz.getParentFile(), "cloudflared");
        if (!extracted.exists()) {
            throw new IOException("tar extraction did not produce a cloudflared binary");
        }
        if (target.exists()) {
            target.delete();
        }
        if (!extracted.renameTo(target)) {
            throw new IOException("cannot move extracted binary to " + target.getAbsolutePath());
        }
    }
}
