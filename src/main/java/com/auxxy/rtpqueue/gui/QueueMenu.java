package com.auxxy.rtpqueue.gui;

import com.auxxy.rtpqueue.RTPQueuePlugin;
import com.auxxy.rtpqueue.config.WorldSettings;
import com.auxxy.rtpqueue.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * World-select GUI for joining the matchmaking queue.
 * MADE BY AUXXY
 */
public final class QueueMenu {

    private final RTPQueuePlugin plugin;

    public QueueMenu(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Slot -> world name. Recomputed from config so it stays correct for
     * every viewer and survives a reload.
     */
    public Map<Integer, String> layout() {
        Map<Integer, String> slots = new HashMap<>();
        int size = plugin.config().queueRows() * 9;
        int fallbackSlot = 10;
        for (WorldSettings settings : plugin.config().worlds().values()) {
            if (!settings.enabled()) {
                continue;
            }
            int slot = settings.slot() >= 0 && settings.slot() < size
                    ? settings.slot()
                    : fallbackSlot++;
            if (slot < size) {
                slots.put(slot, settings.worldName());
            }
        }
        return slots;
    }

    public void open(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.QUEUE, 0);
        int size = plugin.config().queueRows() * 9;
        Inventory inventory = Bukkit.createInventory(holder, size,
                Text.color(plugin.config().queueTitle()));
        holder.inventory(inventory);

        ItemStack filler = simple(plugin.config().queueFiller(), " ", new ArrayList<>());
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler);
        }

        Map<Integer, String> slots = layout();
        for (Map.Entry<Integer, String> entry : slots.entrySet()) {
            WorldSettings settings = plugin.config().world(entry.getValue());
            if (settings == null) {
                continue;
            }
            int slot = entry.getKey();
            boolean loaded = Bukkit.getWorld(settings.worldName()) != null;
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&7World: &f" + settings.worldName()));
            lore.add(Text.color("&7Radius: &f" + settings.minRadius() + " &7- &f" + settings.maxRadius()));
            lore.add(Text.color("&7In queue: &f" + plugin.queues().size(settings.worldName())));
            lore.add("");
            lore.add(Text.color(loaded ? "&aClick to join the queue." : "&cThis world isn't loaded."));
            lore.add(Text.color("&8" + plugin.config().madeBy()));

            inventory.setItem(slot, simple(settings.icon(), settings.display(), lore));
        }

        int leaveSlot = plugin.config().queueLeaveSlot();
        if (leaveSlot >= 0 && leaveSlot < size) {
            List<String> lore = new ArrayList<>();
            String queued = plugin.queues().queuedWorld(player.getUniqueId());
            lore.add(Text.color(queued == null
                    ? "&7You aren't queued right now."
                    : "&7Queued for: &f" + queued));
            lore.add(Text.color("&cClick to leave the queue."));
            lore.add(Text.color("&8" + plugin.config().madeBy()));
            inventory.setItem(leaveSlot,
                    simple(plugin.config().queueLeaveIcon(), "&cLeave queue", lore));
        }

        player.openInventory(inventory);
    }

    /** @return the world bound to a slot, or null. */
    public String worldAt(int slot) {
        return layout().get(slot);
    }

    private ItemStack simple(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
