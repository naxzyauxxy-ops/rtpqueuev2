package com.auxxy.rtpqueue.queue;

import com.auxxy.rtpqueue.RTPQueuePlugin;
import com.auxxy.rtpqueue.config.WorldSettings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds one waiting list per world and pairs players up on a repeating task.
 * MADE BY AUXXY
 */
public final class QueueManager {

    private final RTPQueuePlugin plugin;
    private final Map<String, LinkedHashSet<UUID>> queues = new LinkedHashMap<>();
    private final Map<UUID, String> playerQueue = new LinkedHashMap<>();
    private BukkitTask task;

    public QueueManager(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        int interval = plugin.config().matchmakingIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void clear() {
        queues.clear();
        playerQueue.clear();
    }

    /**
     * @return true when the player was added.
     */
    public boolean join(Player player, String worldName) {
        WorldSettings settings = plugin.config().world(worldName);
        if (settings == null || !settings.enabled()) {
            plugin.messages().send(player, "queue.world-disabled");
            return false;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.messages().send(player, "queue.world-unknown", "world", worldName);
            return false;
        }
        if (plugin.matches().isInMatch(player.getUniqueId())) {
            plugin.messages().send(player, "queue.in-match");
            return false;
        }
        String current = playerQueue.get(player.getUniqueId());
        if (worldName.equals(current)) {
            plugin.messages().send(player, "queue.already-in-queue", "world", worldName);
            return false;
        }
        if (current != null) {
            removeFrom(current, player.getUniqueId());
        }

        long cooldown = plugin.queueCooldowns().remaining(player.getUniqueId());
        if (cooldown > 0 && !player.hasPermission("rtpqueue.bypass.cooldown")) {
            plugin.messages().send(player, "general.cooldown", "seconds", cooldown);
            return false;
        }

        queues.computeIfAbsent(worldName, key -> new LinkedHashSet<>()).add(player.getUniqueId());
        playerQueue.put(player.getUniqueId(), worldName);
        plugin.messages().send(player, "queue.joined",
                "world", worldName, "size", size(worldName));
        announce(worldName, player.getUniqueId());
        return true;
    }

    public boolean leave(Player player) {
        String worldName = playerQueue.remove(player.getUniqueId());
        if (worldName == null) {
            return false;
        }
        removeFrom(worldName, player.getUniqueId());
        announce(worldName, player.getUniqueId());
        return true;
    }

    /** Silent removal used internally when a match starts or a player quits. */
    public void remove(UUID id) {
        String worldName = playerQueue.remove(id);
        if (worldName != null) {
            removeFrom(worldName, id);
        }
    }

    private void removeFrom(String worldName, UUID id) {
        LinkedHashSet<UUID> queue = queues.get(worldName);
        if (queue != null) {
            queue.remove(id);
            if (queue.isEmpty()) {
                queues.remove(worldName);
            }
        }
    }

    public String queuedWorld(UUID id) {
        return playerQueue.get(id);
    }

    public boolean isQueued(UUID id) {
        return playerQueue.containsKey(id);
    }

    public int size(String worldName) {
        LinkedHashSet<UUID> queue = queues.get(worldName);
        return queue == null ? 0 : queue.size();
    }

    public Map<String, Integer> sizes() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<UUID>> entry : queues.entrySet()) {
            result.put(entry.getKey(), entry.getValue().size());
        }
        return result;
    }

    private void announce(String worldName, UUID exclude) {
        if (!plugin.config().announceQueueUpdates()) {
            return;
        }
        LinkedHashSet<UUID> queue = queues.get(worldName);
        if (queue == null) {
            return;
        }
        for (UUID id : new ArrayList<>(queue)) {
            if (id.equals(exclude)) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                plugin.messages().send(player, "queue.update",
                        "world", worldName, "size", queue.size());
            }
        }
    }

    private void tick() {
        for (String worldName : new ArrayList<>(queues.keySet())) {
            LinkedHashSet<UUID> queue = queues.get(worldName);
            if (queue == null) {
                continue;
            }
            while (true) {
                List<Player> pair = takePair(worldName, queue);
                if (pair == null) {
                    break;
                }
                plugin.matches().begin(pair.get(0), pair.get(1), worldName);
            }
        }
    }

    /** Pulls two online, match-ready players out of the queue. */
    private List<Player> takePair(String worldName, LinkedHashSet<UUID> queue) {
        List<Player> found = new ArrayList<>(2);
        for (UUID id : new ArrayList<>(queue)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                queue.remove(id);
                playerQueue.remove(id);
                continue;
            }
            if (plugin.matches().isInMatch(id)) {
                continue;
            }
            found.add(player);
            if (found.size() == 2) {
                break;
            }
        }
        if (found.size() < 2) {
            return null;
        }
        for (Player player : found) {
            queue.remove(player.getUniqueId());
            playerQueue.remove(player.getUniqueId());
        }
        if (queue.isEmpty()) {
            queues.remove(worldName);
        }
        return found;
    }
}
