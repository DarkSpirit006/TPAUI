package dev.darkspirit69.tpaui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

/** Stores a Bedrock player's choice to hide incoming request forms. */
final class BedrockPopupPreferences {
    private final File file;
    private final Logger logger;
    private final Properties preferences = new Properties();

    BedrockPopupPreferences(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    synchronized boolean areEnabled(UUID playerId) {
        String key = playerId + ".request-popups";
        return Boolean.parseBoolean(preferences.getProperty(key, "true"));
    }

    synchronized void setEnabled(UUID playerId, boolean enabled) {
        String key = playerId + ".request-popups";
        if (enabled) {
            preferences.remove(key);
        } else {
            preferences.setProperty(key, "false");
        }
        save();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            preferences.load(input);
        } catch (IOException | IllegalArgumentException ex) {
            logger.warning("Could not load Bedrock pop-up preferences: " + ex.getMessage());
        }
    }

    private void save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Could not create the Bedrock pop-up preferences folder.");
            return;
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            preferences.store(output, "TPAUI Bedrock request pop-up preferences");
        } catch (IOException ex) {
            logger.warning("Could not save Bedrock pop-up preferences: " + ex.getMessage());
        }
    }
}
