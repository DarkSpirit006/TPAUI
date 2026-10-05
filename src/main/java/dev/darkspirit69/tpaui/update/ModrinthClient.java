package dev.darkspirit69.tpaui.update;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Fetches stable release metadata from the TPAUI Modrinth project. */
public final class ModrinthClient {
    private static final int CONNECT_TIMEOUT_MILLIS = 8000;
    private static final int READ_TIMEOUT_MILLIS = 12000;
    private static final Gson GSON = new Gson();
    static final String VERSIONS_URL = "https://api.modrinth.com/v2/project/tpaui/version";
    static final String PROJECT_URL = "https://modrinth.com/plugin/tpaui";

    private final JavaPlugin plugin;
    private final VersionComparator versionComparator;

    public ModrinthClient(JavaPlugin plugin, VersionComparator versionComparator) {
        this.plugin = plugin;
        this.versionComparator = versionComparator;
    }

    public UpdateResult fetchLatestRelease() {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(VERSIONS_URL).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", userAgent());

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                plugin.getLogger().warning("Modrinth update check returned HTTP " + status + ".");
                return UpdateResult.failed();
            }

            String responseBody = readBody(connection);
            return parseVersions(responseBody, PROJECT_URL, versionComparator);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not check Modrinth for updates: " + ex.getMessage());
            return UpdateResult.failed();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Modrinth returned an invalid version response: " + ex.getMessage());
            return UpdateResult.failed();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static UpdateResult parseVersions(
            String responseBody,
            String projectUrl,
            VersionComparator comparator) {
        JsonElement payload = GSON.fromJson(responseBody, JsonElement.class);
        if (!payload.isJsonArray()) {
            return UpdateResult.failed();
        }

        JsonArray versions = payload.getAsJsonArray();
        String latest = null;
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject version = element.getAsJsonObject();
            if (!"release".equalsIgnoreCase(getString(version, "version_type"))) {
                continue;
            }

            String candidate = comparator.normalize(getString(version, "version_number"));
            if (candidate != null && (latest == null || comparator.isNewer(candidate, latest))) {
                latest = candidate;
            }
        }

        return latest == null
                ? UpdateResult.noRelease(projectUrl)
                : UpdateResult.latest(latest, projectUrl);
    }

    private static String getString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private String readBody(HttpURLConnection connection) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    private String userAgent() {
        return "TPAUI/" + plugin.getDescription().getVersion()
                + " (https://github.com/DarkSpirit006/TPAUI)";
    }
}
