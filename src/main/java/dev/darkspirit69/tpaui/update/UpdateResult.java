package dev.darkspirit69.tpaui.update;

/** Result of a Modrinth version lookup. */
public final class UpdateResult {
    private final boolean successful;
    private final String latestVersion;
    private final String projectUrl;

    private UpdateResult(boolean successful, String latestVersion, String projectUrl) {
        this.successful = successful;
        this.latestVersion = latestVersion;
        this.projectUrl = projectUrl;
    }

    public static UpdateResult failed() {
        return new UpdateResult(false, null, null);
    }

    public static UpdateResult noRelease(String projectUrl) {
        return new UpdateResult(true, null, projectUrl);
    }

    public static UpdateResult latest(String version, String projectUrl) {
        return new UpdateResult(true, version, projectUrl);
    }

    public boolean isSuccessful() {
        return successful;
    }

    public boolean hasRelease() {
        return latestVersion != null;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getProjectUrl() {
        return projectUrl;
    }
}
