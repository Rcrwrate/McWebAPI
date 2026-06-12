package love.shirokasoke.webapi.thread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import love.shirokasoke.webapi.MyMod;

/**
 * Checks for mod updates by comparing local build timestamp with the latest GitHub release.
 * <p>
 * https://github.com/Rcrwrate/McWebAPI
 */
public class UpdateChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String GITHUB_API = "https://api.github.com/repos/Rcrwrate/McWebAPI/releases/latest";

    private static volatile Date cachedLocalBuildTime;

    private static class ReleaseInfo {

        final Date publishedAt;
        final String version;
        final String downloadUrl;

        ReleaseInfo(Date publishedAt, String version, String downloadUrl) {
            this.publishedAt = publishedAt;
            this.version = version;
            this.downloadUrl = downloadUrl;
        }
    }

    /** Read local build timestamp from assets/build.json (cached) */
    public static Date readLocalBuildTime() {
        if (cachedLocalBuildTime != null) {
            return cachedLocalBuildTime;
        }
        try (InputStream is = UpdateChecker.class.getResourceAsStream("/assets/build.json")) {
            if (is != null) {
                String json = IOUtils.toString(is, StandardCharsets.UTF_8);
                JsonNode node = MAPPER.readTree(json);
                long ts = node.path("buildTime")
                    .asLong(0);
                if (ts > 0) {
                    cachedLocalBuildTime = new Date(ts * 1000);
                    return cachedLocalBuildTime;
                }
            }
        } catch (Exception e) {
            MyMod.LOG.debug("Could not read build.json: {}", e.getMessage());
        }
        return null;
    }

    public CompletableFuture<Void> checkAsync() {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "UpdateCheck");
            t.setDaemon(true);
            return t;
        });
        return CompletableFuture.runAsync(() -> {
            try {
                checkForUpdate();
            } catch (Exception e) {
                MyMod.LOG.warn("Update check failed: {}", e.getMessage());
            }
        }, executor);
    }

    private void checkForUpdate() {
        Date local = readLocalBuildTime();
        if (local == null) {
            MyMod.LOG.warn("Cannot check for updates: local build time not available");
            return;
        }

        ReleaseInfo release = fetchLatestRelease();
        if (release == null) {
            return;
        }

        if (release.publishedAt != null && release.publishedAt.after(local)) {
            MyMod.LOG.info("========================================");
            MyMod.LOG.info("  A new version of WebAPI is available!");
            MyMod.LOG.info("  Local build:  {}", formatDate(local));
            MyMod.LOG.info("  Latest build: {}", formatDate(release.publishedAt));
            if (!release.version.isEmpty()) {
                MyMod.LOG.info("  Version:       {}", release.version);
            }
            if (!release.downloadUrl.isEmpty()) {
                MyMod.LOG.info("  Download:       {}", release.downloadUrl);
            }
            MyMod.LOG.info("========================================");
        } else {
            MyMod.LOG.info("WebAPI is up to date (built at {}).", formatDate(local));
        }
    }

    private ReleaseInfo fetchLatestRelease() {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(GITHUB_API)
                .toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "WebAPI-UpdateChecker");

            int code = conn.getResponseCode();
            if (code != 200) {
                MyMod.LOG.debug("Update check returned HTTP {}", code);
                return null;
            }

            String body = readResponse(conn);
            JsonNode root = MAPPER.readTree(body);

            String version = root.path("tag_name")
                .asText("");
            if (version.isEmpty()) {
                version = root.path("name")
                    .asText("");
            }

            Date publishedAt = null;
            String publishedAtStr = root.path("published_at")
                .asText("");
            if (!publishedAtStr.isEmpty()) {
                try {
                    SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                    publishedAt = isoFormat.parse(publishedAtStr);
                } catch (Exception e) {
                    MyMod.LOG.debug("Failed to parse published_at '{}': {}", publishedAtStr, e.getMessage());
                }
            }

            String downloadUrl = "";
            JsonNode assets = root.path("assets");
            if (assets.isArray() && assets.size() > 0) {
                downloadUrl = assets.get(0)
                    .path("browser_download_url")
                    .asText("");
                if (downloadUrl.isEmpty()) {
                    downloadUrl = assets.get(0)
                        .path("url")
                        .asText("");
                }
            }

            if (publishedAt != null || !version.isEmpty()) {
                MyMod.LOG.debug("Update check succeeded. Remote: {}", publishedAtStr);
                return new ReleaseInfo(publishedAt, version, downloadUrl);
            }
            return null;
        } catch (IOException e) {
            MyMod.LOG.debug("Update check failed: {}", e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(date);
    }
}
