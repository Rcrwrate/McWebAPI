package love.shirokasoke.webapi.thread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
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

    /** 复用的虚拟线程执行器，避免每次检查都新建线程池（Java 21+） */
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static volatile Instant cachedLocalBuildTime;

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        .withZone(ZoneId.systemDefault());

    private static class ReleaseInfo {

        final Instant publishedAt;
        final String version;
        final String[] downloadUrls;

        ReleaseInfo(Instant publishedAt, String version, String[] downloadUrls) {
            this.publishedAt = publishedAt;
            this.version = version;
            this.downloadUrls = downloadUrls;
        }
    }

    /** Read local build timestamp from assets/build.json (cached) */
    public static Instant readLocalBuildTime() {
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
                    cachedLocalBuildTime = Instant.ofEpochSecond(ts);
                    return cachedLocalBuildTime;
                }
            }
        } catch (Exception e) {
            MyMod.LOG.debug("Could not read build.json: {}", e.getMessage());
        }
        return null;
    }

    public CompletableFuture<Void> checkAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                checkForUpdate();
            } catch (Exception e) {
                MyMod.LOG.warn("Update check failed: {}", e.getMessage());
            }
        }, EXECUTOR);
    }

    private void checkForUpdate() {
        Instant local = readLocalBuildTime();
        if (local == null) {
            MyMod.LOG.warn("Cannot check for updates: local build time not available");
            return;
        }

        ReleaseInfo release = fetchLatestRelease();
        if (release == null) {
            return;
        }

        if (release.publishedAt != null && release.publishedAt.isAfter(local)) {
            MyMod.LOG.info("========================================");
            MyMod.LOG.info("  A new version of WebAPI is available!");
            MyMod.LOG.info("  Local build:  {}", formatDate(local));
            MyMod.LOG.info("  Latest build: {}", formatDate(release.publishedAt));
            if (!release.version.isEmpty()) {
                MyMod.LOG.info("  Version:       {}", release.version);
            }
            if (release.downloadUrls.length > 0) {
                MyMod.LOG.info("  Download:   ");
                for (String downloadUrl : release.downloadUrls) {
                    MyMod.LOG.info("  {}", downloadUrl);
                }
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

            Instant publishedAt = null;
            String publishedAtStr = root.path("published_at")
                .asText("");
            if (!publishedAtStr.isEmpty()) {
                try {
                    // GitHub 返回 UTC ISO-8601 时间戳
                    publishedAt = OffsetDateTime.parse(publishedAtStr)
                        .toInstant();
                } catch (DateTimeParseException e) {
                    MyMod.LOG.debug("Failed to parse published_at '{}': {}", publishedAtStr, e.getMessage());
                }
            }

            List<String> downloadUrls = new ArrayList<>();
            JsonNode assets = root.path("assets");
            if (assets.isArray()) {
                for (JsonNode asset : assets) {
                    String downloadUrl = asset.path("browser_download_url")
                        .asText("");
                    if (downloadUrl.isEmpty()) {
                        downloadUrl = asset.path("url")
                            .asText("");
                    }
                    if (!downloadUrl.isEmpty()) {
                        downloadUrls.add(downloadUrl);
                    }
                }
            }

            if (publishedAt != null || !version.isEmpty()) {
                MyMod.LOG.debug("Update check succeeded. Remote: {}", publishedAtStr);
                return new ReleaseInfo(publishedAt, version, downloadUrls.toArray(String[]::new));
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

    private static String formatDate(Instant instant) {
        return DISPLAY_FORMAT.format(instant);
    }
}
