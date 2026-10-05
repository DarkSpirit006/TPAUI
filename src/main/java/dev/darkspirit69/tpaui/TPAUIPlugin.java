package dev.darkspirit69.tpaui;

import org.bstats.bukkit.Metrics;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import dev.darkspirit69.tpaui.update.UpdateResult;
import dev.darkspirit69.tpaui.update.UpdateService;
import dev.darkspirit69.tpaui.update.VersionComparator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class TPAUIPlugin extends JavaPlugin implements Listener {
    static final String MENU_PERMISSION_DEFAULT = "tpaui.menu";
    private static final int BSTATS_PLUGIN_ID = 34518;

    private GeyserBridge geyserBridge;
    private PlayerSelector playerSelector;
    private TeleportRequestManager requestManager;
    private UpdateService updateService;
    private BedrockPopupPreferences bedrockPopupPreferences;
    private Metrics metrics;
    private SoundManager soundManager;
    private final VersionComparator versionComparator = new VersionComparator();
    private boolean essentialsAvailable;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        soundManager = new SoundManager(this);
        bedrockPopupPreferences = new BedrockPopupPreferences(
                new File(getDataFolder(), "bedrock-preferences.properties"), getLogger());
        Plugin essentials = getServer().getPluginManager().getPlugin("Essentials");
        essentialsAvailable = essentials != null && essentials.isEnabled();
        geyserBridge = new GeyserBridge(this);
        geyserBridge.detect();
        requestManager = new TeleportRequestManager(this, geyserBridge);
        playerSelector = new PlayerSelector(this, geyserBridge);
        updateService = new UpdateService(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(playerSelector, this);
        requestManager.hookEssentialsRequestEvent();
        configureMetrics();
        if (essentialsAvailable) {
            getLogger().info("Enabled with EssentialsX request handling.");
        } else {
            getLogger().info("Enabled in standalone mode; using the built-in teleport request handler.");
        }
    }

    @Override
    public void onDisable() {
        stopMetrics();
    }

    private void configureMetrics() {
        boolean enabled = getConfig().getBoolean("settings.bstats-enabled", true);
        if (enabled == (metrics != null)) {
            return;
        }
        if (!enabled) {
            stopMetrics();
            return;
        }
        try {
            metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        } catch (RuntimeException | LinkageError ex) {
            metrics = null;
            getLogger().warning("Could not initialize bStats metrics: " + ex.getMessage());
        }
    }

    private void stopMetrics() {
        if (metrics == null) {
            return;
        }
        try {
            metrics.shutdown();
        } catch (RuntimeException ex) {
            getLogger().warning("Could not stop bStats metrics: " + ex.getMessage());
        } finally {
            metrics = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ENGLISH);
        if (!"tpaui".equals(commandName)) {
            return handleFallbackCommand(sender, commandName, args);
        }
        return handleAdminCommand(sender, args);
    }

    private boolean handleFallbackCommand(CommandSender sender, String commandName, String[] args) {
        if (essentialsAvailable && requestManager.isFallbackCommand(commandName)) {
            if ("tpa".equals(commandName) && args.length == 0
                    && getConfig().getBoolean("settings.intercept-bare-tpa", true)
                    && sender instanceof Player && canOpenSelector((Player) sender)) {
                playerSelector.openSelector((Player) sender, 0);
                return true;
            }
            dispatchEssentialsCommand(sender, commandName, args);
            return true;
        }
        if (!essentialsAvailable && "tpa".equals(commandName) && args.length == 0
                && !getConfig().getBoolean("settings.intercept-bare-tpa", true)) {
            return false;
        }
        if (!essentialsAvailable && requestManager.isFallbackCommand(commandName)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(message("messages.player-only", "&cOnly players can use this command."));
                return true;
            }
            requestManager.handleFallbackCommand((Player) sender, commandName, args);
            return true;
        }
        return false;
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || (args.length == 1 && "help".equalsIgnoreCase(args[0]))) {
            sender.sendMessage(message("messages.admin-help-title", "&bTPAUI &7- commands"));
            sender.sendMessage(message("messages.admin-help-reload", "&f/tpaui reload &7- reload the configuration"));
            sender.sendMessage(message(
                    "messages.admin-help-version",
                    "&f/tpaui version &7- show version and check Modrinth"));
            return true;
        }
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            return handleReloadCommand(sender);
        }
        if (args.length == 1 && "version".equalsIgnoreCase(args[0])) {
            return handleVersionCommand(sender);
        }
        sender.sendMessage(message("messages.admin-usage", "&cUsage: /tpaui [help|reload|version]"));
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!hasTpauiPermission(sender, "tpaui.admin")) {
            sender.sendMessage(message(
                    "messages.admin-no-permission",
                    "&cYou do not have permission to reload TPAUI."));
            return true;
        }
        reloadConfig();
        configureMetrics();
        updateService = new UpdateService(this);
        sender.sendMessage(message("messages.admin-reloaded", "&aTPAUI configuration reloaded."));
        return true;
    }

    private boolean handleVersionCommand(CommandSender sender) {
        String currentVersion = getDescription().getVersion();
        sender.sendMessage(message("messages.admin-version", "&7TPAUI version &f%version%",
                "%version%", currentVersion));
        sender.sendMessage(message("messages.update-checking", "&7Checking Modrinth for a stable release..."));
        updateService.check(result -> sendUpdateResult(sender, result));
        return true;
    }

    private void sendUpdateResult(CommandSender sender, UpdateResult result) {
        if (sender instanceof Player && !((Player) sender).isOnline()) {
            return;
        }
        if (!result.isSuccessful()) {
            sender.sendMessage(message("messages.update-failed",
                    "&cCould not check Modrinth. See the server log for details."));
            return;
        }
        if (!result.hasRelease()) {
            sender.sendMessage(message("messages.update-no-release",
                    "&7No stable TPAUI release was found on Modrinth."));
            return;
        }

        String latestVersion = result.getLatestVersion();
        String currentVersion = getDescription().getVersion();
        if (versionComparator.isNewer(latestVersion, currentVersion)) {
            String updateMessage = replace(
                    replace(text("messages.update-available",
                            "&eTPAUI %version% is available; this server is running %current%."),
                            "%version%", latestVersion),
                    "%current%", currentVersion);
            sender.sendMessage(color(updateMessage));
            sendUpdateLink(sender, result.getProjectUrl());
            return;
        }

        sender.sendMessage(message("messages.update-current", "&aTPAUI is up to date (%version%).",
                "%version%", currentVersion));
        sendUpdateLink(sender, result.getProjectUrl());
    }

    private void sendUpdateLink(CommandSender sender, String projectUrl) {
        String label = text("messages.update-link-label", "&bOpen the Modrinth project");
        if (!(sender instanceof Player)) {
            sender.sendMessage(color(label) + " " + projectUrl);
            return;
        }

        BaseComponent[] link = TextComponent.fromLegacyText(color(label));
        String hoverText = color(text("messages.update-link-hover", "Open the TPAUI Modrinth page"));
        for (BaseComponent component : link) {
            component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, projectUrl));
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    TextComponent.fromLegacyText(hoverText)));
        }
        ((Player) sender).spigot().sendMessage(link);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ENGLISH);
        if ("tpaui".equals(commandName)) {
            return completeAdminCommand(sender, args);
        }
        if (essentialsAvailable || !requestManager.isFallbackCommand(commandName)
                || !(sender instanceof Player) || args.length != 1) {
            return Collections.emptyList();
        }
        return requestManager.tabComplete((Player) sender, commandName, args[0]);
    }

    private List<String> completeAdminCommand(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> candidates = new ArrayList<String>();
        candidates.add("help");
        candidates.add("version");
        if (hasTpauiPermission(sender, "tpaui.admin")) {
            candidates.add("reload");
        }
        String prefix = args[0].toLowerCase(Locale.ENGLISH);
        List<String> matches = new ArrayList<String>();
        for (String candidate : candidates) {
            if (candidate.startsWith(prefix)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || !message.startsWith("/")) {
            return;
        }
        String trimmed = message.substring(1).trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String[] parts = trimmed.split("\\s+");
        String label = parts[0].toLowerCase(Locale.ENGLISH);
        String[] args = new String[parts.length - 1];
        if (args.length > 0) {
            System.arraycopy(parts, 1, args, 0, args.length);
        }
        Player player = event.getPlayer();

        if (essentialsAvailable) {
            if ("tpacancel".equals(label)) {
                playSound(player, "request-cancelled");
            }
            if (!getConfig().getBoolean("settings.intercept-bare-tpa", true)
                    || !"tpa".equals(label) || args.length != 0) {
                return;
            }
            if (!canOpenSelector(player)) {
                // Let EssentialsX show its normal permission response.
                return;
            }
            event.setCancelled(true);
            playerSelector.openSelector(player, 0);
            return;
        }

        if (!requestManager.isFallbackCommand(label)) {
            return;
        }
        if ("tpa".equals(label) && args.length == 0
                && !getConfig().getBoolean("settings.intercept-bare-tpa", true)) {
            return;
        }
        event.setCancelled(true);
        requestManager.handleFallbackCommand(player, label, args);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!essentialsAvailable) {
            requestManager.clearFallbackRequestsForQuit(event.getPlayer());
        }
    }

    @EventHandler
    public void onEssentialsEnable(PluginEnableEvent event) {
        if (!"Essentials".equalsIgnoreCase(event.getPlugin().getName())) {
            return;
        }
        essentialsAvailable = true;
        requestManager.discardFallbackRequests();
        requestManager.hookEssentialsRequestEvent();
        getLogger().info("EssentialsX was enabled; handing teleport requests to EssentialsX.");
    }

    @EventHandler
    public void onEssentialsDisable(PluginDisableEvent event) {
        if (!"Essentials".equalsIgnoreCase(event.getPlugin().getName())) {
            return;
        }
        essentialsAvailable = false;
        requestManager.unregisterEssentialsRequestEvent();
        requestManager.discardFallbackRequests();
        getLogger().info("EssentialsX was disabled; standalone request handling is active.");
    }

    PlayerSelector selector() {
        return playerSelector;
    }

    TeleportRequestManager requests() {
        return requestManager;
    }

    boolean isEssentialsAvailable() {
        return essentialsAvailable;
    }

    void playSound(Player player, String event) {
        if (soundManager != null) {
            soundManager.play(player, event);
        }
    }

    String menuPermission() {
        return getConfig().getString("settings.menu-permission", MENU_PERMISSION_DEFAULT);
    }

    private boolean canOpenSelector(Player player) {
        String permission = menuPermission();
        if (permission != null && !permission.isEmpty() && !hasTpauiPermission(player, permission)) {
            return false;
        }
        return requestManager.canUseMode(player, RequestMode.TPA)
                || requestManager.canUseMode(player, RequestMode.TPAHERE)
                || playerSelector.canOpenBedrockSettings(player);
    }

    void dispatchEssentialsCommand(CommandSender sender, String commandName, String[] args) {
        Plugin essentials = getServer().getPluginManager().getPlugin("Essentials");
        String namespace = essentials == null ? "essentials" : essentials.getName().toLowerCase(Locale.ENGLISH);
        StringBuilder commandLine = new StringBuilder(namespace).append(':').append(commandName);
        for (String argument : args) {
            commandLine.append(' ').append(argument);
        }
        if (!getServer().dispatchCommand(sender, commandLine.toString())) {
            sender.sendMessage(message(
                    "messages.essentials-command-unavailable",
                    "&cCould not find the EssentialsX command /%command%.",
                    "%command%",
                    commandName));
            getLogger().warning("Could not dispatch EssentialsX command /" + commandName + ".");
        }
    }

    boolean hasTpauiPermission(CommandSender sender, String permission) {
        return !getConfig().getBoolean("settings.check-permissions", true) || sender.hasPermission(permission);
    }

    boolean bedrockRequestPopupsEnabled(Player player) {
        return bedrockPopupPreferences.areEnabled(player.getUniqueId());
    }

    void setBedrockRequestPopupsEnabled(Player player, boolean enabled) {
        bedrockPopupPreferences.setEnabled(player.getUniqueId(), enabled);
    }

    String message(String path, String fallback) {
        return color(text(path, fallback));
    }

    String message(String path, String fallback, String placeholder, String value) {
        return color(replace(text(path, fallback), placeholder, value));
    }

    String text(String path, String fallback) {
        return getConfig().getString(path, fallback);
    }

    String color(String value) {
        return value == null ? "" : org.bukkit.ChatColor.translateAlternateColorCodes('&', value);
    }

    String replace(String text, String placeholder, String value) {
        return text == null ? "" : text.replace(placeholder, value == null ? "" : value);
    }

    int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
