package dev.darkspirit69.tpaui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPopupPreferencesTest {
    private static final Logger LOGGER = Logger.getLogger(BedrockPopupPreferencesTest.class.getName());

    @TempDir
    Path temporaryDirectory;

    @Test
    void preferenceDefaultsToEnabledAndSurvivesReload() {
        UUID playerId = UUID.randomUUID();
        Path file = temporaryDirectory.resolve("bedrock-preferences.properties");
        BedrockPopupPreferences preferences = new BedrockPopupPreferences(file.toFile(), LOGGER);

        assertTrue(preferences.areEnabled(playerId));
        preferences.setEnabled(playerId, false);
        assertFalse(new BedrockPopupPreferences(file.toFile(), LOGGER).areEnabled(playerId));
    }

    @Test
    void playersCanTurnPopupsBackOn() {
        UUID playerId = UUID.randomUUID();
        Path file = temporaryDirectory.resolve("bedrock-preferences.properties");
        BedrockPopupPreferences preferences = new BedrockPopupPreferences(file.toFile(), LOGGER);

        preferences.setEnabled(playerId, false);
        preferences.setEnabled(playerId, true);
        assertTrue(new BedrockPopupPreferences(file.toFile(), LOGGER).areEnabled(playerId));
    }
}
