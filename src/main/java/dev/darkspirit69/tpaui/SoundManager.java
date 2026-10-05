package dev.darkspirit69.tpaui;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resolves configurable sounds from legacy enums or modern Bukkit sound registries. */
final class SoundManager {
    private final TPAUIPlugin plugin;
    private final Map<String, Object> resolvedSounds = new HashMap<String, Object>();
    private final Set<String> warnedEvents = new HashSet<String>();

    SoundManager(TPAUIPlugin plugin) {
        this.plugin = plugin;
    }

    void play(Player player, String event) {
        if (player == null || !player.isOnline()
                || !plugin.getConfig().getBoolean("settings.sounds-enabled", true)) {
            return;
        }

        String defaultName = defaultSound(event);
        String configuredName = plugin.getConfig().getString("sounds." + event, defaultName);
        if (configuredName == null || configuredName.trim().isEmpty()
                || "none".equalsIgnoreCase(configuredName.trim())) {
            return;
        }

        String soundName = configuredName.trim();
        try {
            Class<?> soundType = Class.forName("org.bukkit.Sound");
            Object sound = findSound(soundType, soundName);
            if (sound == null) {
                sound = findSound(soundType, defaultName);
            }
            if (sound == null) {
                sound = findSound(soundType, legacySound(event));
            }
            if (sound == null) {
                warnOnce(event, "No Bukkit sound matches sounds." + event + ": " + soundName + ".");
                return;
            }

            float volume = boundedFloat(plugin.getConfig().getDouble("sounds.volume", 0.8), 0.0f, 1.0f);
            float pitch = boundedFloat(plugin.getConfig().getDouble("sounds.pitch", 1.0), 0.5f, 2.0f);
            if (volume <= 0.0f) {
                return;
            }
            Method playSound = Player.class.getMethod(
                    "playSound", Location.class, soundType, float.class, float.class);
            playSound.invoke(player, player.getLocation(), sound, Float.valueOf(volume), Float.valueOf(pitch));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            warnOnce(event, "Could not play the configured sound for " + event + ": " + ex.getMessage());
        }
    }

    private float boundedFloat(double value, float minimum, float maximum) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return minimum;
        }
        return (float) Math.max(minimum, Math.min(maximum, value));
    }

    private Object findSound(Class<?> soundType, String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String cacheKey = soundType.getName() + ":" + name.toUpperCase(Locale.ENGLISH);
        if (resolvedSounds.containsKey(cacheKey)) {
            return resolvedSounds.get(cacheKey);
        }

        Object sound = findEnumSound(soundType, name);
        if (sound == null) {
            sound = findByValueOf(soundType, name);
        }
        if (sound == null) {
            sound = findInRegistry(name);
        }
        if (sound != null) {
            resolvedSounds.put(cacheKey, sound);
        }
        return sound;
    }

    private Object findEnumSound(Class<?> soundType, String name) {
        if (!soundType.isEnum()) {
            return null;
        }
        Object[] values = soundType.getEnumConstants();
        for (Object value : values) {
            if (value instanceof Enum<?> && ((Enum<?>) value).name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }

    private Object findByValueOf(Class<?> soundType, String name) {
        try {
            Method valueOf = soundType.getMethod("valueOf", String.class);
            return valueOf.invoke(null, name);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private Object findInRegistry(String name) {
        try {
            Class<?> registryClass = Class.forName("org.bukkit.Registry");
            Field soundsField = registryClass.getField("SOUNDS");
            Object registry = soundsField.get(null);
            if (!(registry instanceof Iterable<?>)) {
                return null;
            }

            Class<?> keyedClass = Class.forName("org.bukkit.Keyed");
            Method getKey = keyedClass.getMethod("getKey");
            String requestedKey = normalizeKey(name);
            for (Object sound : (Iterable<?>) registry) {
                Object key = getKey.invoke(sound);
                if (key != null && requestedKey.equals(normalizeKey(key.toString()))) {
                    return sound;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Sound registries are not available on older Bukkit APIs.
        }
        return null;
    }

    private String normalizeKey(String key) {
        String normalized = key.toUpperCase(Locale.ENGLISH);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        return normalized.replace(':', '_').replace('.', '_');
    }

    private String defaultSound(String event) {
        if ("menu-open".equals(event)) {
            return "BLOCK_CHEST_OPEN";
        }
        if ("request-sent".equals(event)) {
            return "UI_BUTTON_CLICK";
        }
        if ("request-received".equals(event) || "request-cancelled".equals(event)) {
            return "BLOCK_NOTE_BLOCK_PLING";
        }
        if ("request-accepted".equals(event)) {
            return "ENTITY_PLAYER_LEVELUP";
        }
        if ("request-denied".equals(event) || "request-expired".equals(event)) {
            return "BLOCK_NOTE_BLOCK_BASS";
        }
        return null;
    }

    private String legacySound(String event) {
        if ("menu-open".equals(event)) {
            return "CHEST_OPEN";
        }
        if ("request-sent".equals(event)) {
            return "CLICK";
        }
        if ("request-received".equals(event) || "request-cancelled".equals(event)) {
            return "NOTE_PLING";
        }
        if ("request-accepted".equals(event)) {
            return "LEVEL_UP";
        }
        if ("request-denied".equals(event) || "request-expired".equals(event)) {
            return "NOTE_BASS";
        }
        return null;
    }

    private void warnOnce(String event, String message) {
        if (warnedEvents.add(event)) {
            plugin.getLogger().warning(message);
        }
    }
}
