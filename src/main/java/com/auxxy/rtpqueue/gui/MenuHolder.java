package com.auxxy.rtpqueue.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as one of ours and remembers which menu it is.
 * MADE BY AUXXY
 */
public final class MenuHolder implements InventoryHolder {

    public enum Type {
        QUEUE
    }

    private final Type type;
    private final int page;
    private Inventory inventory;

    public MenuHolder(Type type, int page) {
        this.type = type;
        this.page = page;
    }

    public Type type() {
        return type;
    }

    public int page() {
        return page;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
