package dev.darkspirit69.tpaui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Inventory state needed to identify selector clicks safely. */
final class MenuHolder implements InventoryHolder {
    final UUID owner;
    final int page;
    final int pageCount;
    final int previousSlot;
    final int closeSlot;
    final int nextSlot;
    final Map<Integer, UUID> targetsBySlot = new HashMap<Integer, UUID>();
    Inventory inventory;

    MenuHolder(UUID owner, int page, int pageCount, int inventorySize) {
        this.owner = owner;
        this.page = page;
        this.pageCount = pageCount;
        int playerSlots = inventorySize - 9;
        this.previousSlot = playerSlots + 3;
        this.closeSlot = playerSlots + 4;
        this.nextSlot = playerSlots + 5;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
