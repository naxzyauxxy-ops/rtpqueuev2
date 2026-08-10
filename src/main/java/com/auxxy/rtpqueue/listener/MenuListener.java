package com.auxxy.rtpqueue.listener;

import com.auxxy.rtpqueue.RTPQueuePlugin;
import com.auxxy.rtpqueue.gui.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Click handling for the queue GUI.
 * MADE BY AUXXY
 */
public final class MenuListener implements Listener {

    private final RTPQueuePlugin plugin;

    public MenuListener(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        if (holder.type() == MenuHolder.Type.QUEUE) {
            handleQueue(player, event.getSlot(), clicked);
        }
    }

    private void handleQueue(Player player, int slot, ItemStack clicked) {
        if (slot == plugin.config().queueLeaveSlot()) {
            if (!plugin.queues().leave(player)) {
                plugin.messages().send(player, "queue.not-in-queue");
            } else {
                plugin.messages().send(player, "queue.left");
            }
            player.closeInventory();
            return;
        }

        String worldName = plugin.queueMenu().worldAt(slot);
        if (worldName == null) {
            return;
        }
        if (Bukkit.getWorld(worldName) == null) {
            plugin.messages().send(player, "queue.world-unknown", "world", worldName);
            return;
        }
        if (plugin.queues().join(player, worldName)) {
            player.closeInventory();
        }
    }
}
