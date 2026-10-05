package dev.darkspirit69.tpaui.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthClientTest {
    @Test
    void usesThePublishedTpauiModrinthProject() {
        assertEquals("https://api.modrinth.com/v2/project/tpaui/version", ModrinthClient.VERSIONS_URL);
        assertEquals("https://modrinth.com/plugin/tpaui", ModrinthClient.PROJECT_URL);
    }

    @Test
    void selectsTheHighestStableRelease() {
        String response = "["
                + "{\"version_number\":\"1.2.0\",\"version_type\":\"release\"},"
                + "{\"version_number\":\"2.0.0-rc1\",\"version_type\":\"beta\"},"
                + "{\"version_number\":\"1.10.0\",\"version_type\":\"release\"},"
                + "{\"version_number\":\"not-a-version\",\"version_type\":\"release\"}"
                + "]";

        UpdateResult result = ModrinthClient.parseVersions(
                response, ModrinthClient.PROJECT_URL, new VersionComparator());

        assertTrue(result.isSuccessful());
        assertTrue(result.hasRelease());
        assertEquals("1.10.0", result.getLatestVersion());
        assertEquals(ModrinthClient.PROJECT_URL, result.getProjectUrl());
    }

    @Test
    void reportsWhenThereIsNoStableRelease() {
        String response = "[{\"version_number\":\"1.0.0-beta1\",\"version_type\":\"beta\"}]";

        UpdateResult result = ModrinthClient.parseVersions(
                response, ModrinthClient.PROJECT_URL, new VersionComparator());

        assertTrue(result.isSuccessful());
        assertFalse(result.hasRelease());
        assertEquals(ModrinthClient.PROJECT_URL, result.getProjectUrl());
    }
}
