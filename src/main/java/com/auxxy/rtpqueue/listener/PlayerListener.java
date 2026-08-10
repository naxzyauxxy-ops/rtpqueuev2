package com.auxxy.rtpqueue.listener;

import com.auxxy.rtpqueue.RTPQueuePlugin;
import com.auxxy.rtpqueue.match.Match;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps queues and matches in sync with what players actually do.
 * MADE BY AUXXY
 */
public final class PlayerListener implements Listener {

    private final RTPQueuePlugin plugin;

    public PlayerListener(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.queues().remove(player.getUniqueId());

        Match match = plugin.matches().matchOf(player.getUniqueId());
        if (match != null) {
            plugin.matches().handleDisconnect(match, player.getUniqueId());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Match match = plugin.matches().matchOf(player.getUniqueId());
        if (match == null) {
            return;
        }
        plugin.matches().finish(match, match.opponentOf(player.getUniqueId()), player.getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!plugin.config().leaveOnWorldChange()) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.matches().isInMatch(player.getUniqueId())) {
            return;
        }
        if (plugin.queues().isQueued(player.getUniqueId())) {
            plugin.queues().leave(player);
            plugin.messages().send(player, "queue.left");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }

        Match victimMatch = plugin.matches().matchOf(victim.getUniqueId());
        Match attackerMatch = plugin.matches().matchOf(attacker.getUniqueId());

        // Nobody involved is in a match - leave normal PvP alone.
        if (victimMatch == null && attackerMatch == null) {
            return;
        }
        // Someone is mid-match but they aren't fighting each other.
        if (victimMatch == null || attackerMatch == null || victimMatch != attackerMatch) {
            event.setCancelled(true);
            return;
        }
        if (!victimMatch.damageAllowed()) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
