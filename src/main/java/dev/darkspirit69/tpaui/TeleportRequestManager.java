package dev.darkspirit69.tpaui;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Handles teleport requests and the optional EssentialsX request event. */
final class TeleportRequestManager {
    private final TPAUIPlugin plugin;
    private final GeyserBridge geyserBridge;
    private boolean essentialsEventHooked;
    private boolean essentialsResponseEventHooked;
    private final PendingRequestStore pendingRequests = new PendingRequestStore();
    private final Map<UUID, Long> fallbackLastRequestAt = new HashMap<UUID, Long>();
    private final Listener essentialsEventListener = new Listener() { };

    TeleportRequestManager(TPAUIPlugin plugin, GeyserBridge geyserBridge) {
        this.plugin = plugin;
        this.geyserBridge = geyserBridge;
    }

    boolean canUseMode(Player player, RequestMode mode) {
        String action = mode == RequestMode.TPA ? "tpa" : "tpahere";
        String permission = plugin.isEssentialsAvailable()
                ? permissionNode("permissions.essentials." + action, "essentials." + action)
                : permissionNode("permissions.standalone." + action, "tpaui." + action);
        return plugin.hasTpauiPermission(player, permission);
    }

    private boolean canAccept(Player player) {
        String permission = plugin.isEssentialsAvailable()
                ? permissionNode("permissions.essentials.accept", "essentials.tpaccept")
                : permissionNode("permissions.standalone.accept", "tpaui.accept");
        return plugin.hasTpauiPermission(player, permission);
    }

    private boolean canDeny(Player player) {
        String permission = plugin.isEssentialsAvailable()
                ? permissionNode("permissions.essentials.deny", "essentials.tpdeny")
                : permissionNode("permissions.standalone.deny", "tpaui.deny");
        return plugin.hasTpauiPermission(player, permission);
    }

    private boolean canCancel(Player player) {
        String permission = plugin.isEssentialsAvailable()
                ? permissionNode("permissions.essentials.cancel", "essentials.tpacancel")
                : permissionNode("permissions.standalone.cancel", "tpaui.cancel");
        return plugin.hasTpauiPermission(player, permission);
    }

    private String permissionNode(String path, String fallback) {
        String value = plugin.getConfig().getString(path, fallback);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    void issueRequest(Player requester, RequestMode mode, String targetName) {
        if (!requester.isOnline()) {
            return;
        }
        if (!canUseMode(requester, mode)) {
            requester.sendMessage(plugin.message(
                    "messages.no-permission",
                    "&cYou do not have permission to use that request type."));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            requester.sendMessage(plugin.message(
                    "messages.target-offline",
                    "&cThat player is no longer online. Please choose again."));
            plugin.selector().openSelector(requester, 0);
            return;
        }
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        if (plugin.isEssentialsAvailable()) {
            // Use the explicit namespace in case TPAUI's declared command owns the unqualified label.
            plugin.dispatchEssentialsCommand(requester, mode.command, new String[] {target.getName()});
        } else {
            createFallbackRequest(requester, target, mode);
        }
    }

    boolean isFallbackCommand(String label) {
        return "tpa".equals(label) || "tpahere".equals(label)
                || "tpaccept".equals(label) || "tpdeny".equals(label)
                || "tpacancel".equals(label);
    }

    List<String> tabComplete(Player player, String label, String prefix) {
        if (player == null || label == null) {
            return Collections.emptyList();
        }

        List<String> candidates = new ArrayList<String>();
        if (("tpa".equals(label) && canUseMode(player, RequestMode.TPA))
                || ("tpahere".equals(label) && canUseMode(player, RequestMode.TPAHERE))) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    candidates.add(online.getName());
                }
            }
        } else if ("tpaccept".equals(label) && canAccept(player)) {
            addIncomingNames(player, candidates);
        } else if ("tpdeny".equals(label) && canDeny(player)) {
            addIncomingNames(player, candidates);
        } else if ("tpacancel".equals(label) && canCancel(player)) {
            for (TeleportRequest request : pendingRequests.outgoingFor(player.getUniqueId())) {
                if (Bukkit.getPlayer(request.targetId) != null) {
                    candidates.add(request.targetName);
                }
            }
        }
        return filterCandidates(candidates, prefix);
    }

    private void addIncomingNames(Player player, List<String> candidates) {
        for (TeleportRequest request : pendingRequests.incomingFor(player.getUniqueId())) {
            if (Bukkit.getPlayer(request.requesterId) != null) {
                candidates.add(request.requesterName);
            }
        }
    }

    private List<String> filterCandidates(List<String> candidates, String prefix) {
        String partial = prefix == null ? "" : prefix.toLowerCase(Locale.ENGLISH);
        List<String> matches = new ArrayList<String>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ENGLISH).startsWith(partial)) {
                matches.add(candidate);
            }
        }
        Collections.sort(matches, String.CASE_INSENSITIVE_ORDER);
        return matches;
    }

    void handleFallbackCommand(Player player, String label, String[] args) {
        if ("tpa".equals(label)) {
            if (args.length == 0) {
                String menuPermission = plugin.menuPermission();
                if (menuPermission != null && !menuPermission.isEmpty()
                        && !plugin.hasTpauiPermission(player, menuPermission)) {
                    player.sendMessage(plugin.message(
                            "messages.no-permission",
                            "&cYou do not have permission to use that request type."));
                    return;
                }
                if (!canUseMode(player, RequestMode.TPA) && !canUseMode(player, RequestMode.TPAHERE)
                        && !plugin.selector().canOpenBedrockSettings(player)) {
                    player.sendMessage(plugin.message(
                            "messages.no-permission",
                            "&cYou do not have permission to use that request type."));
                    return;
                }
                plugin.selector().openSelector(player, 0);
            } else if (args.length == 1) {
                issueRequest(player, RequestMode.TPA, args[0]);
            } else {
                player.sendMessage(plugin.message("messages.usage-tpa", "&cUsage: /tpa <player>"));
            }
            return;
        }
        if ("tpahere".equals(label)) {
            if (args.length == 1) {
                issueRequest(player, RequestMode.TPAHERE, args[0]);
            } else {
                player.sendMessage(plugin.message("messages.usage-tpahere", "&cUsage: /tpahere <player>"));
            }
            return;
        }
        if ("tpaccept".equals(label)) {
            if (args.length > 1) {
                player.sendMessage(plugin.message("messages.usage-tpaccept", "&cUsage: /tpaccept [player]"));
            } else {
                acceptFallbackRequest(player, args.length == 0 ? null : args[0]);
            }
            return;
        }
        if ("tpdeny".equals(label)) {
            if (args.length > 1) {
                player.sendMessage(plugin.message("messages.usage-tpdeny", "&cUsage: /tpdeny [player]"));
            } else {
                denyFallbackRequest(player, args.length == 0 ? null : args[0]);
            }
            return;
        }
        if ("tpacancel".equals(label)) {
            if (args.length > 1) {
                player.sendMessage(plugin.message("messages.usage-tpacancel", "&cUsage: /tpacancel [player]"));
            } else {
                cancelFallbackRequests(player, args.length == 0 ? null : args[0]);
            }
        }
    }

    private void createFallbackRequest(final Player requester, final Player target, RequestMode mode) {
        if (!canAccept(target)) {
            requester.sendMessage(plugin.message("messages.target-cannot-accept",
                    "&c%player% cannot accept teleport requests.", "%player%", target.getName()));
            return;
        }
        UUID requesterId = requester.getUniqueId();
        UUID targetId = target.getUniqueId();
        if (pendingRequests.hasOutgoing(requesterId, targetId)) {
            requester.sendMessage(plugin.message("messages.request-already-pending",
                    "&cYou already have a pending request to %player%.", "%player%", target.getName()));
            return;
        }
        int maximumOutgoing = Math.max(0, plugin.getConfig().getInt("settings.fallback-max-pending-outgoing", 5));
        if (maximumOutgoing > 0 && pendingRequests.outgoingCount(requesterId) >= maximumOutgoing) {
            requester.sendMessage(plugin.message("messages.too-many-pending-requests",
                    "&cYou already have the maximum of %count% pending requests.",
                    "%count%", String.valueOf(maximumOutgoing)));
            return;
        }
        int configuredCooldown = plugin.getConfig().getInt("settings.fallback-request-cooldown-seconds", 3);
        int cooldownSeconds = plugin.clamp(configuredCooldown, 0, 3600);
        long now = System.currentTimeMillis();
        Long lastRequestAt = fallbackLastRequestAt.get(requesterId);
        if (cooldownSeconds > 0 && lastRequestAt != null) {
            long waitMillis = cooldownSeconds * 1000L - (now - lastRequestAt.longValue());
            if (waitMillis > 0L) {
                long waitSeconds = (waitMillis + 999L) / 1000L;
                requester.sendMessage(plugin.message("messages.request-cooldown",
                        "&cPlease wait %seconds% seconds before sending another request.",
                        "%seconds%", String.valueOf(waitSeconds)));
                return;
            }
        }
        fallbackLastRequestAt.put(requesterId, Long.valueOf(now));

        final TeleportRequest request = new TeleportRequest(requesterId, targetId,
                requester.getName(), target.getName(), mode, now);
        if (!pendingRequests.add(request)) {
            requester.sendMessage(plugin.message("messages.request-already-pending",
                    "&cYou already have a pending request to %player%.", "%player%", target.getName()));
            return;
        }

        plugin.playSound(requester, "request-sent");
        plugin.playSound(target, "request-received");
        showReceiverActions(target, requester.getName(), mode == RequestMode.TPAHERE);
        showRequesterCancel(requester, target.getName());

        long configuredExpiration = plugin.getConfig().getLong(
                "settings.fallback-request-expiration-seconds", 60L);
        long expirationSeconds = Math.max(1L, configuredExpiration);
        long expirationTicks = Math.min(expirationSeconds, 86400L) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!isFallbackRequestPending(request)) {
                    return;
                }
                removeFallbackRequest(request);
                Player currentRequester = Bukkit.getPlayer(request.requesterId);
                Player currentTarget = Bukkit.getPlayer(request.targetId);
                if (currentRequester != null && currentRequester.isOnline()) {
                    currentRequester.sendMessage(plugin.message("messages.request-expired-sender",
                            "&cYour request to %player% expired.", "%player%", request.targetName));
                    plugin.playSound(currentRequester, "request-expired");
                }
                if (currentTarget != null && currentTarget.isOnline()) {
                    currentTarget.sendMessage(plugin.message("messages.request-expired-target",
                            "&7The request from %player% expired.", "%player%", request.requesterName));
                    plugin.playSound(currentTarget, "request-expired");
                }
            }
        }, expirationTicks);
    }

    private TeleportRequest findIncomingRequest(Player recipient, String requesterName) {
        return pendingRequests.findIncoming(recipient.getUniqueId(), requesterName);
    }

    private void acceptFallbackRequest(Player recipient, String requesterName) {
        if (!canAccept(recipient)) {
            recipient.sendMessage(plugin.message(
                    "messages.no-permission",
                    "&cYou do not have permission to use that request type."));
            return;
        }
        TeleportRequest request = findIncomingRequest(recipient, requesterName);
        if (request == null || !isFallbackRequestPending(request)) {
            recipient.sendMessage(plugin.message(
                    "messages.no-pending-request",
                    "&cThere is no matching pending teleport request."));
            return;
        }
        Player requester = Bukkit.getPlayer(request.requesterId);
        if (requester == null || !requester.isOnline()) {
            removeFallbackRequest(request);
            recipient.sendMessage(plugin.message(
                    "messages.requester-offline",
                    "&cThat requester is no longer online."));
            return;
        }

        Player teleporter = request.mode == RequestMode.TPA ? requester : recipient;
        Player destination = request.mode == RequestMode.TPA ? recipient : requester;
        if (!teleporter.teleport(destination.getLocation())) {
            recipient.sendMessage(plugin.message("messages.teleport-failed", "&cThe teleport could not be completed."));
            requester.sendMessage(plugin.message("messages.teleport-failed", "&cThe teleport could not be completed."));
            return;
        }
        removeFallbackRequest(request);
        recipient.sendMessage(plugin.message("messages.request-accepted-target",
                "&aYou accepted %player%'s teleport request.", "%player%", requester.getName()));
        requester.sendMessage(plugin.message("messages.request-accepted-sender",
                "&a%player% accepted your teleport request.", "%player%", recipient.getName()));
        plugin.playSound(recipient, "request-accepted");
        plugin.playSound(requester, "request-accepted");
    }

    private void denyFallbackRequest(Player recipient, String requesterName) {
        if (!canDeny(recipient)) {
            recipient.sendMessage(plugin.message(
                    "messages.no-permission",
                    "&cYou do not have permission to use that request type."));
            return;
        }
        TeleportRequest request = findIncomingRequest(recipient, requesterName);
        if (request == null || !isFallbackRequestPending(request)) {
            recipient.sendMessage(plugin.message(
                    "messages.no-pending-request",
                    "&cThere is no matching pending teleport request."));
            return;
        }
        removeFallbackRequest(request);
        recipient.sendMessage(plugin.message("messages.request-denied-target",
                "&7You denied %player%'s teleport request.", "%player%", request.requesterName));
        Player requester = Bukkit.getPlayer(request.requesterId);
        plugin.playSound(recipient, "request-denied");
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(plugin.message("messages.request-denied-sender",
                    "&c%player% denied your teleport request.", "%player%", recipient.getName()));
            plugin.playSound(requester, "request-denied");
        }
    }

    private void cancelFallbackRequests(Player requester, String targetName) {
        if (!canCancel(requester)) {
            requester.sendMessage(plugin.message(
                    "messages.no-permission",
                    "&cYou do not have permission to use that request type."));
            return;
        }
        List<TeleportRequest> requests = pendingRequests.outgoingFor(requester.getUniqueId());
        if (requests.isEmpty()) {
            requester.sendMessage(plugin.message(
                    "messages.no-outgoing-request",
                    "&cYou have no pending teleport requests to cancel."));
            return;
        }
        List<TeleportRequest> cancelled = new ArrayList<TeleportRequest>();
        for (TeleportRequest request : requests) {
            if (targetName == null || request.targetName.equalsIgnoreCase(targetName)) {
                cancelled.add(request);
            }
        }
        if (cancelled.isEmpty()) {
            requester.sendMessage(plugin.message(
                    "messages.no-outgoing-request",
                    "&cYou have no pending teleport requests to cancel."));
            return;
        }
        for (TeleportRequest request : cancelled) {
            removeFallbackRequest(request);
            Player target = Bukkit.getPlayer(request.targetId);
            if (target != null && target.isOnline()) {
                target.sendMessage(plugin.message("messages.request-cancelled-target",
                        "&7%player% cancelled their teleport request.", "%player%", requester.getName()));
                plugin.playSound(target, "request-cancelled");
            }
        }
        plugin.playSound(requester, "request-cancelled");
        if (cancelled.size() == 1) {
            requester.sendMessage(plugin.message("messages.request-cancelled-sender",
                    "&aCancelled your request to %player%.", "%player%", cancelled.get(0).targetName));
        } else {
            requester.sendMessage(plugin.color(plugin.replace(plugin.text("messages.requests-cancelled-sender",
                    "&aCancelled %count% pending teleport requests."), "%count%", String.valueOf(cancelled.size()))));
        }
    }

    private boolean isFallbackRequestPending(TeleportRequest request) {
        return pendingRequests.isPending(request);
    }

    private void removeFallbackRequest(TeleportRequest request) {
        pendingRequests.remove(request);
    }

    void discardFallbackRequests() {
        pendingRequests.clear();
        fallbackLastRequestAt.clear();
    }

    void clearFallbackRequestsForQuit(Player player) {
        UUID playerId = player.getUniqueId();
        fallbackLastRequestAt.remove(playerId);

        List<TeleportRequest> outgoing = pendingRequests.outgoingFor(playerId);
        for (TeleportRequest request : outgoing) {
            removeFallbackRequest(request);
            Player target = Bukkit.getPlayer(request.targetId);
            if (target != null && target.isOnline()) {
                target.sendMessage(plugin.message("messages.request-cancelled-offline-target",
                        "&7%player%'s request was removed because they left.", "%player%", player.getName()));
            }
        }

        List<TeleportRequest> incoming = pendingRequests.incomingFor(playerId);
        for (TeleportRequest request : incoming) {
            removeFallbackRequest(request);
            Player requester = Bukkit.getPlayer(request.requesterId);
            if (requester != null && requester.isOnline()) {
                requester.sendMessage(plugin.message("messages.request-target-offline-sender",
                        "&c%player% left; your teleport request was removed.", "%player%", player.getName()));
            }
        }
    }

    void hookEssentialsRequestEvent() {
        if (essentialsEventHooked && essentialsResponseEventHooked) {
            return;
        }
        Plugin essentials = plugin.getServer().getPluginManager().getPlugin("Essentials");
        if (essentials == null || !essentials.isEnabled()) {
            plugin.getLogger().info("EssentialsX not detected; standalone request handling is active.");
            return;
        }
        hookEssentialsResponseEvent(essentials);
        if (essentialsEventHooked) {
            return;
        }

        String[] eventClassNames = new String[] {
                "net.ess3.api.events.TPARequestEvent",
                "com.earth2me.essentials.events.TPARequestEvent",
                "com.earth2me.essentials.api.events.TPARequestEvent"
        };
        Class<?> eventClass = null;
        for (String className : eventClassNames) {
            try {
                eventClass = Class.forName(className, true, essentials.getClass().getClassLoader());
                break;
            } catch (ClassNotFoundException | RuntimeException | LinkageError ignored) {
                // Try the next known package name.
            }
        }
        if (eventClass == null || !Event.class.isAssignableFrom(eventClass)) {
            plugin.getLogger().warning(
                    "EssentialsX's TPARequestEvent was not found; request action buttons are unavailable.");
            return;
        }

        Class<? extends Event> typedEvent = eventClass.asSubclass(Event.class);
        PluginManager manager = plugin.getServer().getPluginManager();
        manager.registerEvent(typedEvent, essentialsEventListener, EventPriority.MONITOR, new EventExecutor() {
            @Override
            public void execute(Listener listener, Event event) {
                handleEssentialsRequest(event);
            }
        }, plugin);
        essentialsEventHooked = true;
        plugin.getLogger().info("Hooked EssentialsX TPARequestEvent for request actions.");
    }

    private void hookEssentialsResponseEvent(Plugin essentials) {
        if (essentialsResponseEventHooked) {
            return;
        }
        String className = "net.essentialsx.api.v2.events.TeleportRequestResponseEvent";
        Class<?> eventClass;
        try {
            eventClass = Class.forName(className, true, essentials.getClass().getClassLoader());
        } catch (ClassNotFoundException | RuntimeException | LinkageError ignored) {
            return;
        }
        if (!Event.class.isAssignableFrom(eventClass)) {
            return;
        }

        Class<? extends Event> typedEvent = eventClass.asSubclass(Event.class);
        PluginManager manager = plugin.getServer().getPluginManager();
        manager.registerEvent(typedEvent, essentialsEventListener, EventPriority.MONITOR, new EventExecutor() {
            @Override
            public void execute(Listener listener, Event event) {
                handleEssentialsResponse(event);
            }
        }, plugin);
        essentialsResponseEventHooked = true;
        plugin.getLogger().info("Hooked EssentialsX teleport response event for sounds.");
    }

    private void handleEssentialsResponse(Event event) {
        if (Boolean.TRUE.equals(invokeNoArg(event, "isCancelled"))) {
            return;
        }
        Object acceptValue = invokeNoArg(event, "isAccept");
        if (!(acceptValue instanceof Boolean)) {
            return;
        }
        Player requestee = extractPlayer(invokeNoArg(event, "getRequestee"), 0);
        Player requester = extractPlayer(invokeNoArg(event, "getRequester"), 0);
        if (requestee == null || requester == null) {
            return;
        }
        String sound = Boolean.TRUE.equals(acceptValue) ? "request-accepted" : "request-denied";
        plugin.playSound(requestee, sound);
        plugin.playSound(requester, sound);
    }

    private void handleEssentialsRequest(Event event) {
        Object cancelled = invokeNoArg(event, "isCancelled");
        if (Boolean.TRUE.equals(cancelled)) {
            return;
        }
        Object requesterObject = invokeNoArg(event, "getRequester");
        Object targetObject = invokeNoArg(event, "getTarget");
        final Player requester = extractPlayer(requesterObject, 0);
        final Player target = extractPlayer(targetObject, 0);
        if (requester == null || target == null) {
            return;
        }
        Object hereValue = invokeNoArg(event, "isTeleportHere", "isTpaHere", "isTpahere");
        final boolean isHere = Boolean.TRUE.equals(hereValue);
        final String requesterName = requester.getName();
        final String targetName = target.getName();
        long delay = Math.max(0L, plugin.getConfig().getLong("settings.request-button-delay-ticks", 1L));
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!plugin.isEnabled() || !requester.isOnline() || !target.isOnline()) {
                    return;
                }
                plugin.playSound(requester, "request-sent");
                plugin.playSound(target, "request-received");
                showReceiverActions(target, requesterName, isHere);
                showRequesterCancel(requester, targetName);
            }
        }, delay);
    }

    private void showReceiverActions(final Player receiver, final String requesterName, boolean isHere) {
        String template = isHere
                ? plugin.text("messages.request-receiver-tpahere", "&e%player% &7asked you to teleport to them.")
                : plugin.text("messages.request-receiver-tpa", "&e%player% &7requested to teleport to you.");
        String body = plugin.replace(template, "%player%", requesterName);
        boolean bedrock = geyserBridge != null && geyserBridge.isBedrockPlayer(receiver);
        if (bedrock) {
            if (plugin.getConfig().getBoolean("settings.use-geyser-forms", true)
                    && plugin.bedrockRequestPopupsEnabled(receiver)) {
                String formPath = isHere ? "messages.form-request-tpahere" : "messages.form-request-tpa";
                String formContent = plugin.replace(
                        plugin.text(formPath, "%player% requested to teleport to you."),
                        "%player%",
                        requesterName);
                String formTitle = plugin.text("messages.form-title", "Teleport request");
                if (showBedrockRequestForm(receiver, requesterName, formTitle, formContent)) {
                    return;
                }
            }
            sendBedrockRequestInstructions(receiver, body, requesterName);
            return;
        }

        sendClickable(receiver, body,
                new ChatButton(plugin.text("messages.chat-accept-label", "[Accept]"),
                        configuredChatColor("messages.chat-accept-color", ChatColor.GREEN),
                        "/tpaccept " + requesterName,
                        plugin.text("messages.chat-accept-hover", "Accept this request")),
                new ChatButton(plugin.text("messages.chat-deny-label", "[Deny]"),
                        configuredChatColor("messages.chat-deny-color", ChatColor.RED),
                        "/tpdeny " + requesterName,
                        plugin.text("messages.chat-deny-hover", "Deny this request")));
    }

    private boolean showBedrockRequestForm(
            final Player receiver, final String requesterName, final String formTitle, final String formContent) {
        List<GeyserBridge.FormButton> buttons = new ArrayList<GeyserBridge.FormButton>();
        String acceptLabel = plugin.text("messages.form-accept", "Accept");
        buttons.add(new GeyserBridge.FormButton(acceptLabel, new GeyserBridge.FormAction() {
            @Override
            public void run() {
                handleBedrockRequestAction(receiver, requesterName, true);
            }
        }));
        String denyLabel = plugin.text("messages.form-deny", "Deny");
        buttons.add(new GeyserBridge.FormButton(denyLabel, new GeyserBridge.FormAction() {
            @Override
            public void run() {
                handleBedrockRequestAction(receiver, requesterName, false);
            }
        }));
        String settingsLabel = plugin.text("messages.form-request-popups-settings", "Incoming pop-up settings");
        buttons.add(new GeyserBridge.FormButton(settingsLabel, new GeyserBridge.FormAction() {
            @Override
            public void run() {
                openBedrockRequestSettings(receiver, requesterName, formTitle, formContent);
            }
        }));
        return geyserBridge.showSimpleForm(receiver, formTitle, formContent, buttons);
    }

    private void openBedrockRequestSettings(
            final Player receiver, final String requesterName, final String formTitle, final String formContent) {
        String title = plugin.text("messages.form-request-popups-settings-title", "Incoming request pop-ups");
        String content = plugin.text(
                "messages.form-request-popups-settings-content",
                "Use the switch to show or hide incoming TPA request forms.");
        String toggleLabel = plugin.text(
                "messages.form-request-popups-toggle", "Show incoming TPA request pop-ups");
        boolean currentlyEnabled = plugin.bedrockRequestPopupsEnabled(receiver);
        boolean shown = geyserBridge.showCustomForm(
                receiver,
                title,
                content,
                toggleLabel,
                currentlyEnabled,
                new GeyserBridge.CustomFormAction() {
                    @Override
                    public void run(boolean enabled) {
                        updateBedrockPopupPreference(receiver, enabled);
                        if (!showBedrockRequestForm(receiver, requesterName, formTitle, formContent)) {
                            sendBedrockRequestInstructions(receiver, formContent, requesterName);
                        }
                    }
                },
                new GeyserBridge.FormAction() {
                    @Override
                    public void run() {
                        if (!showBedrockRequestForm(receiver, requesterName, formTitle, formContent)) {
                            sendBedrockRequestInstructions(receiver, formContent, requesterName);
                        }
                    }
                });
        if (!shown) {
            receiver.sendMessage(plugin.message(
                    "messages.bedrock-popup-settings-unavailable",
                    "&cThe pop-up switch could not be opened. You can change it from the /tpa selector."));
            if (!showBedrockRequestForm(receiver, requesterName, formTitle, formContent)) {
                sendBedrockRequestInstructions(receiver, formContent, requesterName);
            }
        }
    }

    private void updateBedrockPopupPreference(Player receiver, boolean enabled) {
        boolean changed = plugin.bedrockRequestPopupsEnabled(receiver) != enabled;
        if (!changed) {
            return;
        }
        plugin.setBedrockRequestPopupsEnabled(receiver, enabled);
        String messagePath = enabled
                ? "messages.bedrock-request-popups-enabled"
                : "messages.bedrock-request-popups-disabled";
        String fallback = enabled
                ? "&aIncoming request pop-ups are enabled."
                : "&eIncoming request pop-ups are disabled. Future requests will show /tpaccept and /tpdeny.";
        receiver.sendMessage(plugin.message(messagePath, fallback));
    }

    private void handleBedrockRequestAction(Player receiver, String requesterName, boolean accept) {
        if (!receiver.isOnline()) {
            return;
        }
        if (plugin.isEssentialsAvailable()) {
            String command = accept ? "tpaccept" : "tpdeny";
            plugin.dispatchEssentialsCommand(receiver, command, new String[] {requesterName});
        } else if (accept) {
            acceptFallbackRequest(receiver, requesterName);
        } else {
            denyFallbackRequest(receiver, requesterName);
        }
    }

    private void sendBedrockRequestInstructions(Player receiver, String formContent, String requesterName) {
        receiver.sendMessage(plugin.message("messages.prefix", "&8[&bTPA&8] ") + plugin.color(formContent));
        receiver.sendMessage(plugin.message(
                "messages.bedrock-accept-fallback",
                "&7Use &f/tpaccept %player% &7or &f/tpdeny %player%&7.",
                "%player%",
                requesterName));
    }

    private void showRequesterCancel(final Player requester, final String targetName) {
        String template = plugin.text("messages.request-sender", "&7Teleport request sent to &e%player%&7.");
        String body = plugin.replace(template, "%player%", targetName);
        // Bedrock users never receive an addon cancel form/button. Standalone mode may show plain command guidance.
        if (geyserBridge != null && geyserBridge.isBedrockPlayer(requester)) {
            if (!plugin.isEssentialsAvailable()) {
                requester.sendMessage(plugin.message("messages.prefix", "&8[&bTPA&8] ") + plugin.color(body));
                if (canCancel(requester)) {
                    requester.sendMessage(plugin.message("messages.bedrock-cancel-command",
                            "&7To cancel, use &f/tpacancel %player%&7.", "%player%", targetName));
                }
            }
            return;
        }
        if (!canCancel(requester)) {
            if (!plugin.isEssentialsAvailable()) {
                requester.sendMessage(plugin.message("messages.prefix", "&8[&bTPA&8] ") + plugin.color(body));
            }
            return;
        }
        sendClickable(requester, body,
                new ChatButton(plugin.text("messages.chat-cancel-label", "[Cancel]"),
                        configuredChatColor("messages.chat-cancel-color", ChatColor.GRAY),
                        "/tpacancel " + targetName,
                        plugin.text("messages.chat-cancel-hover", "Cancel this request")));
    }

    private void sendClickable(Player player, String body, ChatButton... buttons) {
        List<BaseComponent> components = new ArrayList<BaseComponent>();
        String prefix = plugin.text("messages.prefix", "&8[&bTPA&8] ");
        BaseComponent[] prefixComponents = TextComponent.fromLegacyText(plugin.color(prefix));
        Collections.addAll(components, prefixComponents);
        BaseComponent[] bodyComponents = TextComponent.fromLegacyText(plugin.color(body));
        Collections.addAll(components, bodyComponents);
        components.add(new TextComponent(" "));
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) {
                components.add(new TextComponent(" "));
            }
            ChatButton spec = buttons[i];
            BaseComponent[] buttonParts = TextComponent.fromLegacyText(plugin.color(spec.label));
            for (BaseComponent button : buttonParts) {
                if (button.getColorRaw() == null) {
                    button.setColor(spec.color);
                }
                button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, spec.command));
                button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        TextComponent.fromLegacyText(plugin.color("&f" + spec.hoverText))));
                components.add(button);
            }
        }
        player.spigot().sendMessage(components.toArray(new BaseComponent[components.size()]));
    }

    private Player extractPlayer(Object value, int depth) {
        if (value == null || depth > 4) {
            return null;
        }
        if (value instanceof Player) {
            return (Player) value;
        }
        String[] accessors = new String[] {"getPlayer", "getBase", "getSender", "getCommandSender", "getSource"};
        for (String accessor : accessors) {
            Object nested = invokeNoArg(value, accessor);
            if (nested != null && nested != value) {
                Player player = extractPlayer(nested, depth + 1);
                if (player != null) {
                    return player;
                }
            }
        }
        return null;
    }

    private Object invokeNoArg(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String name : methodNames) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Try the next method name supported by another EssentialsX version.
            }
        }
        return null;
    }

    private ChatColor configuredChatColor(String path, ChatColor fallback) {
        String value = plugin.getConfig().getString(path, fallback.name());
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return ChatColor.valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(
                    "Invalid chat color at " + path + ": " + value + ". Using " + fallback.name() + ".");
            return fallback;
        }
    }

    void unregisterEssentialsRequestEvent() {
        if (!essentialsEventHooked && !essentialsResponseEventHooked) {
            return;
        }
        HandlerList.unregisterAll(essentialsEventListener);
        essentialsEventHooked = false;
        essentialsResponseEventHooked = false;
    }
}
