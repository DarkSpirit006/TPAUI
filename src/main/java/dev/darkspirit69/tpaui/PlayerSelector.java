package dev.darkspirit69.tpaui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Builds the Java inventory and optional Bedrock forms used to select a target. */
final class PlayerSelector implements Listener {
    private static final int INVENTORY_SIZE_MIN = 18;
    private static final int INVENTORY_SIZE_MAX = 54;
    private static final int LEGACY_INVENTORY_TITLE_MAX = 32;

    private final TPAUIPlugin plugin;
    private final GeyserBridge geyserBridge;

    PlayerSelector(TPAUIPlugin plugin, GeyserBridge geyserBridge) {
        this.plugin = plugin;
        this.geyserBridge = geyserBridge;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        MenuHolder holder = (MenuHolder) top.getHolder();
        if (!holder.owner.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        UUID targetId = holder.targetsBySlot.get(Integer.valueOf(rawSlot));
        if (targetId != null) {
            ClickType click = event.getClick();
            RequestMode mode;
            if (click.isRightClick()) {
                mode = RequestMode.TPAHERE;
            } else if (click.isLeftClick()) {
                mode = RequestMode.TPA;
            } else {
                return;
            }

            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline()) {
                player.sendMessage(plugin.message("messages.target-offline", "&cThat player is no longer online."));
                openInventoryMenu(player, holder.page);
                return;
            }
            player.closeInventory();
            plugin.requests().issueRequest(player, mode, target.getName());
            return;
        }

        if (rawSlot == holder.previousSlot && holder.page > 0) {
            openInventoryMenu(player, holder.page - 1);
        } else if (rawSlot == holder.closeSlot) {
            player.closeInventory();
        } else if (rawSlot == holder.nextSlot && holder.page < holder.pageCount - 1) {
            openInventoryMenu(player, holder.page + 1);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof MenuHolder)) {
            return;
        }
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot.intValue() < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    boolean canOpenBedrockSettings(Player player) {
        return plugin.getConfig().getBoolean("settings.use-geyser-forms", true)
                && geyserBridge != null && geyserBridge.isBedrockPlayer(player);
    }

    void openSelector(Player player, int page) {
        boolean canTpa = plugin.requests().canUseMode(player, RequestMode.TPA);
        boolean canTpahere = plugin.requests().canUseMode(player, RequestMode.TPAHERE);
        boolean bedrockForms = canOpenBedrockSettings(player);
        if (!canTpa && !canTpahere && !bedrockForms) {
            return;
        }
        plugin.playSound(player, "menu-open");

        if (bedrockForms && openGeyserModeForm(player)) {
            return;
        }
        if (!canTpa && !canTpahere) {
            return;
        }
        openInventoryMenu(player, page);
    }

    private boolean openGeyserModeForm(final Player player) {
        List<GeyserBridge.FormButton> buttons = new ArrayList<GeyserBridge.FormButton>();
        if (plugin.requests().canUseMode(player, RequestMode.TPA)) {
            String tpaLabel = plugin.text("messages.form-mode-tpa", "Teleport to a player");
            buttons.add(new GeyserBridge.FormButton(tpaLabel, new GeyserBridge.FormAction() {
                @Override
                public void run() {
                    openGeyserPlayerForm(player, RequestMode.TPA, 0);
                }
            }));
        }
        if (plugin.requests().canUseMode(player, RequestMode.TPAHERE)) {
            String tpahereLabel = plugin.text("messages.form-mode-tpahere", "Ask a player to come to you");
            buttons.add(new GeyserBridge.FormButton(tpahereLabel, new GeyserBridge.FormAction() {
                @Override
                public void run() {
                    openGeyserPlayerForm(player, RequestMode.TPAHERE, 0);
                }
            }));
        }

        String settingsLabel = plugin.text("messages.form-request-popups-settings", "Incoming pop-up settings");
        buttons.add(new GeyserBridge.FormButton(settingsLabel, new GeyserBridge.FormAction() {
            @Override
            public void run() {
                openGeyserPopupSettings(player);
            }
        }));

        return geyserBridge.showSimpleForm(player,
                plugin.text("messages.form-select-mode-title", "Teleport menu"),
                plugin.text("messages.form-select-mode-content",
                        "Choose TPA or TPAHERE, or adjust incoming request pop-ups."),
                buttons);
    }

    private boolean openGeyserPopupSettings(final Player player) {
        boolean currentlyEnabled = plugin.bedrockRequestPopupsEnabled(player);
        String title = plugin.text("messages.form-request-popups-settings-title", "Incoming request pop-ups");
        String content = plugin.text(
                "messages.form-request-popups-settings-content",
                "Use the switch to show or hide incoming TPA request forms.");
        String toggleLabel = plugin.text(
                "messages.form-request-popups-toggle", "Show incoming TPA request pop-ups");
        boolean shown = geyserBridge.showCustomForm(
                player,
                title,
                content,
                toggleLabel,
                currentlyEnabled,
                new GeyserBridge.CustomFormAction() {
                    @Override
                    public void run(boolean enabled) {
                        updateBedrockPopupPreference(player, enabled);
                    }
                },
                new GeyserBridge.FormAction() {
                    @Override
                    public void run() {
                        openGeyserModeForm(player);
                    }
                });
        if (shown) {
            return true;
        }

        // Older Cumulus builds may not support CustomForm toggles; retain a usable fallback.
        boolean enableChoice = !currentlyEnabled;
        String fallbackLabel = plugin.text(
                enableChoice ? "messages.form-request-popups-enable" : "messages.form-request-popups-disable",
                enableChoice ? "Enable incoming request pop-ups" : "Disable incoming request pop-ups");
        List<GeyserBridge.FormButton> fallbackButtons = new ArrayList<GeyserBridge.FormButton>();
        fallbackButtons.add(new GeyserBridge.FormButton(fallbackLabel, new GeyserBridge.FormAction() {
            @Override
            public void run() {
                updateBedrockPopupPreference(player, enableChoice);
            }
        }));
        return geyserBridge.showSimpleForm(player, title, content, fallbackButtons);
    }

    private void updateBedrockPopupPreference(Player player, boolean enabled) {
        boolean changed = plugin.bedrockRequestPopupsEnabled(player) != enabled;
        plugin.setBedrockRequestPopupsEnabled(player, enabled);
        if (changed) {
            String messagePath = enabled
                    ? "messages.bedrock-request-popups-enabled"
                    : "messages.bedrock-request-popups-disabled";
            String fallback = enabled
                    ? "&aIncoming request pop-ups are enabled."
                    : "&eIncoming request pop-ups are disabled. Use /tpaccept or /tpdeny.";
            player.sendMessage(plugin.message(messagePath, fallback));
        }
        openGeyserModeForm(player);
    }

    private boolean openGeyserPlayerForm(final Player player, final RequestMode mode, int requestedPage) {
        if (!plugin.requests().canUseMode(player, mode)) {
            player.sendMessage(plugin.message(
                    "messages.no-permission",
                    "&cYou do not have permission to use that request type."));
            return true;
        }
        List<Player> online = getSelectablePlayers(player);
        if (online.isEmpty()) {
            player.sendMessage(plugin.message(
                    "messages.no-online-players",
                    "&cThere are no other online players to select."));
            return true;
        }

        int configuredPageSize = plugin.getConfig().getInt("settings.geyser-players-per-page", 15);
        int pageSize = plugin.clamp(configuredPageSize, 5, 30);
        int pageCount = Math.max(1, (online.size() + pageSize - 1) / pageSize);
        int page = plugin.clamp(requestedPage, 0, pageCount - 1);
        int start = page * pageSize;
        int end = Math.min(online.size(), start + pageSize);

        List<GeyserBridge.FormButton> buttons = new ArrayList<GeyserBridge.FormButton>();
        if (page > 0) {
            final int previous = page - 1;
            String previousLabel = plugin.text("messages.form-previous-page", "Previous page");
            buttons.add(new GeyserBridge.FormButton(previousLabel, new GeyserBridge.FormAction() {
                @Override
                public void run() {
                    openGeyserPlayerForm(player, mode, previous);
                }
            }));
        }
        for (int index = start; index < end; index++) {
            final String targetName = online.get(index).getName();
            buttons.add(new GeyserBridge.FormButton(targetName, new GeyserBridge.FormAction() {
                @Override
                public void run() {
                    plugin.requests().issueRequest(player, mode, targetName);
                }
            }));
        }
        if (end < online.size()) {
            final int next = page + 1;
            String nextLabel = plugin.text("messages.form-next-page", "Next page");
            buttons.add(new GeyserBridge.FormButton(nextLabel, new GeyserBridge.FormAction() {
                @Override
                public void run() {
                    openGeyserPlayerForm(player, mode, next);
                }
            }));
        }
        boolean canUseBothModes = plugin.requests().canUseMode(player, RequestMode.TPA)
                && plugin.requests().canUseMode(player, RequestMode.TPAHERE);
        if (canUseBothModes) {
            String backLabel = plugin.text("messages.form-back-to-modes", "Back to request type");
            buttons.add(new GeyserBridge.FormButton(backLabel, new GeyserBridge.FormAction() {
                @Override
                public void run() {
                    openGeyserModeForm(player);
                }
            }));
        }

        String title = plugin.text("messages.form-select-player-title", "Choose a player");
        String modeNameKey = mode == RequestMode.TPA
                ? "messages.form-mode-name-tpa"
                : "messages.form-mode-name-tpahere";
        String defaultModeName = mode == RequestMode.TPA ? "TPA" : "TPAHERE";
        String modeName = plugin.text(modeNameKey, defaultModeName);
        String contentTemplate = plugin.text(
                "messages.form-select-player-content", "%mode% - Page %page%");
        String content = plugin.replace(contentTemplate, "%mode%", modeName);
        content = plugin.replace(content, "%page%", String.valueOf(page + 1));
        boolean sent = geyserBridge.showSimpleForm(player, title, content, buttons);
        if (!sent) {
            openInventoryMenu(player, page);
        }
        return sent;
    }

    private void openInventoryMenu(Player player, int requestedPage) {
        List<Player> online = getSelectablePlayers(player);
        if (online.isEmpty()) {
            player.sendMessage(plugin.message(
                    "messages.no-online-players",
                    "&cThere are no other online players to select."));
            return;
        }

        int inventorySize = getInventorySize();
        int playerSlots = inventorySize - 9;
        int configuredPageSize = plugin.getConfig().getInt("settings.inventory-players-per-page", 45);
        int pageSize = plugin.clamp(configuredPageSize, 1, playerSlots);
        int pageCount = Math.max(1, (online.size() + pageSize - 1) / pageSize);
        int page = plugin.clamp(requestedPage, 0, pageCount - 1);

        MenuHolder holder = new MenuHolder(player.getUniqueId(), page, pageCount, inventorySize);
        Inventory inventory = Bukkit.createInventory(holder, inventorySize, getInventoryTitle());
        holder.inventory = inventory;

        int start = page * pageSize;
        int end = Math.min(online.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            Player target = online.get(index);
            int slot = index - start;
            inventory.setItem(slot, playerItem(target));
            holder.targetsBySlot.put(Integer.valueOf(slot), target.getUniqueId());
        }

        ItemStack filler = item(
                plugin.text("settings.filler-material", "GRAY_STAINED_GLASS_PANE"),
                plugin.text("settings.filler-legacy-material", "STAINED_GLASS_PANE"),
                (short) plugin.getConfig().getInt("settings.filler-data", 7),
                " ", Collections.<String>emptyList());
        for (int slot = playerSlots; slot < inventorySize; slot++) {
            inventory.setItem(slot, filler);
        }

        String previousLabel = page > 0
                ? plugin.text("messages.inventory-previous", "&ePrevious page")
                : plugin.text("messages.inventory-inactive", "&7No page in that direction.");
        inventory.setItem(holder.previousSlot, item(
                plugin.text("settings.previous-item-material", "ARROW"),
                plugin.text("settings.previous-item-legacy-material", "ARROW"),
                (short) plugin.getConfig().getInt("settings.previous-item-data", 0),
                previousLabel,
                Collections.<String>emptyList()));
        inventory.setItem(holder.closeSlot, item(
                plugin.text("settings.close-item-material", "BARRIER"),
                plugin.text("settings.close-item-legacy-material", "REDSTONE"),
                (short) plugin.getConfig().getInt("settings.close-item-data", 0),
                plugin.text("messages.inventory-close", "&cClose"), Collections.<String>emptyList()));
        String nextLabel = page < pageCount - 1
                ? plugin.text("messages.inventory-next", "&eNext page")
                : plugin.text("messages.inventory-inactive", "&7No page in that direction.");
        inventory.setItem(holder.nextSlot, item(
                plugin.text("settings.next-item-material", "ARROW"),
                plugin.text("settings.next-item-legacy-material", "ARROW"),
                (short) plugin.getConfig().getInt("settings.next-item-data", 0),
                nextLabel,
                Collections.<String>emptyList()));

        player.openInventory(inventory);
    }

    private int getInventorySize() {
        int configured = plugin.clamp(plugin.getConfig().getInt("settings.inventory-size", 54),
                INVENTORY_SIZE_MIN, INVENTORY_SIZE_MAX);
        int normalized = configured - (configured % 9);
        return plugin.clamp(normalized, INVENTORY_SIZE_MIN, INVENTORY_SIZE_MAX);
    }

    private String getInventoryTitle() {
        String title = plugin.color(plugin.text("settings.inventory-title", "&8Teleport Menu"));
        if (title.length() > LEGACY_INVENTORY_TITLE_MAX) {
            title = title.substring(0, LEGACY_INVENTORY_TITLE_MAX);
            if (title.endsWith(String.valueOf(org.bukkit.ChatColor.COLOR_CHAR))) {
                title = title.substring(0, title.length() - 1);
            }
        }
        return title;
    }

    private ItemStack playerItem(Player target) {
        Material material = getMaterial(plugin.text("settings.player-item-material", "PLAYER_HEAD"));
        short durability = (short) plugin.getConfig().getInt("settings.player-item-data", 0);
        if (material == null) {
            material = getMaterial(plugin.text("settings.player-item-legacy-material", "SKULL_ITEM"));
            durability = (short) plugin.getConfig().getInt("settings.player-item-legacy-data", 3);
        }
        if (material == null) {
            material = Material.PAPER;
            durability = 0;
        }
        ItemStack stack = new ItemStack(material, 1, durability);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta) {
            setSkullOwner((SkullMeta) meta, target);
        }
        if (meta != null) {
            String nameTemplate = plugin.text("messages.inventory-player-name", "&e%player%");
            String name = plugin.replace(nameTemplate, "%player%", target.getName());
            meta.setDisplayName(plugin.color(name));
            List<String> lore = new ArrayList<String>();
            lore.add(plugin.text("messages.inventory-player-left-lore", "&aLeft-click: send TPA"));
            lore.add(plugin.text("messages.inventory-player-right-lore", "&bRight-click: send TPAHERE"));
            meta.setLore(colorList(lore));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void setSkullOwner(SkullMeta meta, Player target) {
        try {
            Method method = SkullMeta.class.getMethod("setOwningPlayer", OfflinePlayer.class);
            method.invoke(meta, target);
            return;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // setOwningPlayer was added after legacy Bukkit versions.
        }
        try {
            SkullMeta.class.getMethod("setOwner", String.class).invoke(meta, target.getName());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // A plain head still works as the selector icon if a fork changes this API.
        }
    }

    private Material getMaterial(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        return Material.getMaterial(name.trim().toUpperCase(Locale.ENGLISH));
    }

    private ItemStack item(String preferred, String fallback, short durability, String displayName, List<String> lore) {
        Material material = getMaterial(preferred);
        short data = durability;
        if (material == null) {
            material = getMaterial(fallback);
        } else if (!"STAINED_GLASS_PANE".equalsIgnoreCase(preferred)) {
            data = 0;
        }
        if (material == null) {
            material = Material.PAPER;
            data = 0;
        }
        ItemStack stack = new ItemStack(material, 1, data);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color(displayName));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(colorList(lore));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<String> colorList(List<String> input) {
        List<String> result = new ArrayList<String>();
        for (String line : input) {
            result.add(plugin.color(line));
        }
        return result;
    }

    private List<Player> getSelectablePlayers(Player viewer) {
        List<Player> players = new ArrayList<Player>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != null && online.isOnline() && !online.getUniqueId().equals(viewer.getUniqueId())) {
                players.add(online);
            }
        }
        Collections.sort(players, new Comparator<Player>() {
            @Override
            public int compare(Player left, Player right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return players;
    }
}
