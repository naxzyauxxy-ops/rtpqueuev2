package com.auxxy.rtpqueue.match;

import com.auxxy.rtpqueue.RTPQueuePlugin;
import com.auxxy.rtpqueue.config.PluginConfig;
import com.auxxy.rtpqueue.config.WorldSettings;
import com.auxxy.rtpqueue.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs matches: safe-spot search, teleport, countdown, grace period and cleanup.
 * MADE BY AUXXY
 */
public final class MatchManager {

    private final RTPQueuePlugin plugin;
    private final Map<UUID, Match> byPlayer = new HashMap<>();
    private final List<Match> active = new ArrayList<>();
    private final Map<Match, List<BukkitTask>> tasks = new HashMap<>();

    public MatchManager(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isInMatch(UUID id) {
        return byPlayer.containsKey(id);
    }

    public Match matchOf(UUID id) {
        return byPlayer.get(id);
    }

    /** Starts the search + teleport flow for two players. */
    public void begin(Player first, Player second, String worldName) {
        World world = Bukkit.getWorld(worldName);
        WorldSettings settings = plugin.config().world(worldName);
        if (world == null || settings == null) {
            plugin.messages().send(first, "queue.world-unknown", "world", worldName);
            plugin.messages().send(second, "queue.world-unknown", "world", worldName);
            return;
        }

        plugin.messages().send(first, "queue.match-found", "opponent", second.getName());
        plugin.messages().send(second, "queue.match-found", "opponent", first.getName());
        plugin.messages().send(first, "queue.searching", "world", worldName);
        plugin.messages().send(second, "queue.searching", "world", worldName);

        // Never let a player end up in two matches at once: that is what leaves
        // the old one dangling and reports "you are already in a match" forever.
        if (isInMatch(first.getUniqueId())) {
            forceClear(first.getUniqueId());
        }
        if (isInMatch(second.getUniqueId())) {
            forceClear(second.getUniqueId());
        }

        Match match = new Match(first.getUniqueId(), second.getUniqueId(), worldName,
                first.getLocation().clone(), second.getLocation().clone());
        register(match);

        plugin.locations().find(world, settings).thenAccept(spot -> {
            if (spot == null) {
                failSearch(match);
                return;
            }
            plugin.locations()
                    .findNear(world, spot.getBlockX(), spot.getBlockZ(), plugin.config().spread())
                    .thenAccept(other -> teleportIn(match, spot, other == null ? spot.clone() : other));
        });
    }

    private void register(Match match) {
        byPlayer.put(match.first(), match);
        byPlayer.put(match.second(), match);
        active.add(match);
        tasks.put(match, new ArrayList<>());
        plugin.queues().remove(match.first());
        plugin.queues().remove(match.second());
    }

    private void failSearch(Match match) {
        Player first = Bukkit.getPlayer(match.first());
        Player second = Bukkit.getPlayer(match.second());
        plugin.messages().send(first, "queue.search-failed");
        plugin.messages().send(second, "queue.search-failed");
        cleanup(match);
    }

    private void teleportIn(Match match, Location firstSpot, Location secondSpot) {
        Player first = Bukkit.getPlayer(match.first());
        Player second = Bukkit.getPlayer(match.second());
        if (first == null || second == null) {
            handleDisconnect(match, first == null ? match.first() : match.second());
            return;
        }

        face(firstSpot, secondSpot);
        face(secondSpot, firstSpot);

        plugin.messages().send(first, "match.teleporting");
        plugin.messages().send(second, "match.teleporting");

        prepare(first);
        prepare(second);

        match.spots(firstSpot.clone(), secondSpot.clone());

        first.teleportAsync(firstSpot);
        second.teleportAsync(secondSpot);

        startCountdown(match);
        startWatcher(match);
    }

    private void prepare(Player player) {
        PluginConfig config = plugin.config();
        if (config.healOnStart()) {
            player.setHealth(maxHealth(player));
        }
        if (config.feedOnStart()) {
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
        }
        if (config.extinguishOnStart()) {
            player.setFireTicks(0);
        }
        if (config.clearEffectsOnStart()) {
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setFallDistance(0.0F);
    }

    private double maxHealth(Player player) {
        try {
            AttributeInstance instance = player.getAttribute(Attribute.MAX_HEALTH);
            if (instance != null) {
                return instance.getValue();
            }
        } catch (Throwable ignored) {
            // older API mappings - fall back below
        }
        return player.getMaxHealth();
    }

    private void face(Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null) {
            return;
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        from.setYaw(yaw);
        from.setPitch(0.0F);
    }

    private void startCountdown(Match match) {
        int seconds = plugin.config().countdownSeconds();
        match.state(Match.State.COUNTDOWN);
        if (seconds <= 0) {
            beginGrace(match);
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            private int remaining = seconds;

            @Override
            public void run() {
                if (match.state() == Match.State.ENDED) {
                    cancel();
                    return;
                }
                Player first = Bukkit.getPlayer(match.first());
                Player second = Bukkit.getPlayer(match.second());
                if (first == null || second == null) {
                    cancel();
                    handleDisconnect(match, first == null ? match.first() : match.second());
                    return;
                }
                if (remaining <= 0) {
                    cancel();
                    beginGrace(match);
                    return;
                }
                sendCountdown(first, second, remaining);
                sendCountdown(second, first, remaining);
                remaining--;
            }
        }.runTaskTimer(plugin, 5L, 20L);
        track(match, task);
    }

    /**
     * Watches a running match once per second for two things:
     * a player wandering too far from the arena, and a player who is no longer
     * online. Either used to leave the match stuck in place.
     */
    private void startWatcher(Match match) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (match.state() == Match.State.ENDED) {
                    cancel();
                    return;
                }

                Player first = Bukkit.getPlayer(match.first());
                Player second = Bukkit.getPlayer(match.second());
                if (first == null || second == null) {
                    cancel();
                    handleDisconnect(match, first == null ? match.first() : match.second());
                    return;
                }

                int leash = plugin.config().leashBlocks();
                if (leash <= 0) {
                    return;
                }

                for (Player player : new Player[]{first, second}) {
                    Location spot = match.spot(player.getUniqueId());
                    if (spot == null || spot.getWorld() == null) {
                        continue;
                    }
                    Location at = player.getLocation();

                    // A different world always counts as having left the arena.
                    boolean gone = at.getWorld() == null
                            || !at.getWorld().getUID().equals(spot.getWorld().getUID())
                            || at.distanceSquared(spot) > (double) leash * leash;

                    if (gone) {
                        cancel();
                        leftArena(match, player.getUniqueId());
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
        track(match, task);
    }

    /** Someone strayed past the leash radius: end the match and free them both. */
    private void leftArena(Match match, UUID strayId) {
        if (match.state() == Match.State.ENDED) {
            return;
        }
        match.state(Match.State.ENDED);

        Player stray = Bukkit.getPlayer(strayId);
        Player other = Bukkit.getPlayer(match.opponentOf(strayId));

        if (stray != null) {
            plugin.messages().send(stray, "match.left-arena");
        }
        if (other != null) {
            plugin.messages().send(other, "match.opponent-left-arena");
            runCommands(plugin.config().winnerCommands(), other.getName(),
                    stray != null ? stray.getName() : "?", match.worldName());
        }
        scheduleReturn(match);
    }

    /**
     * Removes a player from whatever match they are in immediately, without the
     * return-teleport delay. Used by /rtpqueue leave and to clear stale state.
     */
    public boolean forceClear(UUID id) {
        Match match = byPlayer.get(id);
        if (match == null) {
            return false;
        }
        match.state(Match.State.ENDED);
        Player other = Bukkit.getPlayer(match.opponentOf(id));
        if (other != null) {
            plugin.messages().send(other, "match.opponent-left");
        }
        cleanup(match);
        return true;
    }

    private void sendCountdown(Player player, Player opponent, int remaining) {
        plugin.messages().title(player, "match.countdown-title", "match.countdown-subtitle",
                "seconds", remaining, "opponent", opponent.getName());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.0F);
    }

    private void beginGrace(Match match) {
        Player first = Bukkit.getPlayer(match.first());
        Player second = Bukkit.getPlayer(match.second());
        if (first == null || second == null) {
            handleDisconnect(match, first == null ? match.first() : match.second());
            return;
        }

        int grace = plugin.config().graceSeconds();
        match.state(grace > 0 ? Match.State.GRACE : Match.State.FIGHTING);

        for (Player player : new Player[]{first, second}) {
            plugin.messages().title(player, "match.start-title", "match.start-subtitle");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
            if (grace > 0) {
                plugin.messages().send(player, "match.grace", "seconds", grace);
            }
        }

        if (grace > 0) {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (match.state() != Match.State.ENDED) {
                    match.state(Match.State.FIGHTING);
                }
            }, grace * 20L);
            track(match, task);
        }

        int timeout = plugin.config().timeoutSeconds();
        if (timeout > 0) {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (match.state() != Match.State.ENDED) {
                    draw(match);
                }
            }, timeout * 20L);
            track(match, task);
        }
    }

    /** Ends the match with a winner. */
    public void finish(Match match, UUID winnerId, UUID loserId) {
        if (match.state() == Match.State.ENDED) {
            return;
        }
        match.state(Match.State.ENDED);

        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(loserId);
        String winnerName = winner != null ? winner.getName() : "?";
        String loserName = loser != null ? loser.getName() : "?";

        if (winner != null) {
            plugin.messages().send(winner, "match.won", "opponent", loserName);
        }
        if (loser != null) {
            plugin.messages().send(loser, "match.lost", "opponent", winnerName);
        }

        runCommands(plugin.config().winnerCommands(), winnerName, loserName, match.worldName());
        runCommands(plugin.config().loserCommands(), winnerName, loserName, match.worldName());

        scheduleReturn(match);
    }

    private void draw(Match match) {
        if (match.state() == Match.State.ENDED) {
            return;
        }
        match.state(Match.State.ENDED);
        for (UUID id : new UUID[]{match.first(), match.second()}) {
            Player player = Bukkit.getPlayer(id);
            plugin.messages().send(player, "match.timeout");
        }
        scheduleReturn(match);
    }

    /** Called when someone logs out mid-match. */
    public void handleDisconnect(Match match, UUID quitterId) {
        if (match.state() == Match.State.ENDED) {
            cleanup(match);
            return;
        }
        // Free the player who left right away; the survivor is released by
        // scheduleReturn below. Waiting for both left the quitter marked as
        // "in a match" until they rejoined.
        byPlayer.remove(quitterId);
        match.state(Match.State.ENDED);
        Player remaining = Bukkit.getPlayer(match.opponentOf(quitterId));
        if (remaining != null) {
            plugin.messages().send(remaining, "match.opponent-left");
            runCommands(plugin.config().winnerCommands(), remaining.getName(), "?", match.worldName());
        }
        scheduleReturn(match);
    }

    private void runCommands(List<String> commands, String winner, String loser, String world) {
        for (String raw : commands) {
            String command = Text.placeholders(raw, "winner", winner, "loser", loser, "world", world);
            if (command.isBlank()) {
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private void scheduleReturn(Match match) {
        int delay = plugin.config().returnDelaySeconds();
        long cooldown = plugin.config().cooldownSeconds();

        for (UUID id : new UUID[]{match.first(), match.second()}) {
            plugin.queueCooldowns().set(id, cooldown);
            Player player = Bukkit.getPlayer(id);
            if (player != null && plugin.config().returnToOrigin() && delay > 0) {
                plugin.messages().send(player, "match.returning", "seconds", delay);
            }
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.config().returnToOrigin()) {
                for (UUID id : new UUID[]{match.first(), match.second()}) {
                    Player player = Bukkit.getPlayer(id);
                    Location origin = match.origin(id);
                    if (player != null && origin != null && origin.getWorld() != null) {
                        player.teleportAsync(origin);
                        plugin.messages().send(player, "match.returned");
                    }
                }
            }
            cleanup(match);
        }, Math.max(1L, delay * 20L));
        track(match, task);
    }

    private void track(Match match, BukkitTask task) {
        tasks.computeIfAbsent(match, key -> new ArrayList<>()).add(task);
    }

    private void cleanup(Match match) {
        byPlayer.remove(match.first());
        byPlayer.remove(match.second());
        active.remove(match);
        List<BukkitTask> matchTasks = tasks.remove(match);
        if (matchTasks != null) {
            for (BukkitTask task : matchTasks) {
                task.cancel();
            }
        }
    }

    /** Ends everything - used on plugin disable. */
    public void shutdown() {
        for (Match match : new ArrayList<>(active)) {
            match.state(Match.State.ENDED);
            if (plugin.config().returnToOrigin()) {
                for (UUID id : new UUID[]{match.first(), match.second()}) {
                    Player player = Bukkit.getPlayer(id);
                    Location origin = match.origin(id);
                    if (player != null && origin != null && origin.getWorld() != null) {
                        player.teleport(origin);
                    }
                }
            }
            cleanup(match);
        }
        byPlayer.clear();
        active.clear();
        tasks.clear();
    }
}
