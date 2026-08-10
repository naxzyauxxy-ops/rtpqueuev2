package com.auxxy.rtpqueue.command;

import com.auxxy.rtpqueue.RTPQueuePlugin;
import com.auxxy.rtpqueue.config.WorldSettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /rtpqueue and /rtpq.
 * MADE BY AUXXY
 */
public final class QueueCommand implements CommandExecutor, TabCompleter {

    private final RTPQueuePlugin plugin;

    public QueueCommand(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    /** Joins the default world without showing the GUI. */
    private void joinDirect(Player player) {
        String world = plugin.config().defaultWorld();
        if (world == null || world.isBlank()) {
            var worlds = plugin.config().worlds();
            if (worlds.isEmpty()) {
                plugin.messages().send(player, "queue.world-unknown", "world", "-");
                return;
            }
            world = worlds.keySet().iterator().next();
        }
        WorldSettings settings = plugin.config().world(world);
        if (settings == null) {
            plugin.messages().send(player, "queue.world-unknown", "world", world);
            return;
        }
        plugin.queues().join(player, world);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "general.players-only");
                return true;
            }
            if (plugin.config().menuEnabled()) {
                plugin.queueMenu().open(player);
            } else {
                // Menu disabled: join the configured default world directly.
                joinDirect(player);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "about", "info", "version" -> {
                plugin.messages().sendPlain(sender, "general.about",
                        "version", plugin.version());
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("rtpqueue.reload")) {
                    plugin.messages().send(sender, "general.no-permission");
                    return true;
                }
                plugin.reloadEverything();
                plugin.messages().send(sender, "general.reloaded");
                return true;
            }
            case "list" -> {
                Map<String, Integer> sizes = plugin.queues().sizes();
                if (sizes.isEmpty()) {
                    plugin.messages().send(sender, "queue.list-empty");
                    return true;
                }
                plugin.messages().send(sender, "queue.list-header");
                for (Map.Entry<String, Integer> entry : sizes.entrySet()) {
                    plugin.messages().send(sender, "queue.list-entry",
                            "world", entry.getKey(), "size", entry.getValue());
                }
                return true;
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "general.players-only");
                    return true;
                }
                if (plugin.queues().leave(player)) {
                    plugin.messages().send(player, "queue.left");
                } else if (plugin.matches().forceClear(player.getUniqueId())) {
                    // Also the escape hatch if match state ever gets stuck.
                    plugin.messages().send(player, "match.left-arena");
                } else {
                    plugin.messages().send(player, "queue.not-in-queue");
                }
                return true;
            }
            case "join" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "general.players-only");
                    return true;
                }
                if (args.length < 2) {
                    plugin.queueMenu().open(player);
                    return true;
                }
                plugin.queues().join(player, args[1]);
                return true;
            }
            default -> {
                plugin.messages().send(sender, "general.unknown-subcommand");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : new String[]{"join", "leave", "list", "about"}) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    options.add(sub);
                }
            }
            if (sender.hasPermission("rtpqueue.reload") && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                options.add("reload");
            }
            return options;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            for (WorldSettings settings : plugin.config().worlds().values()) {
                if (settings.enabled()
                        && settings.worldName().toLowerCase(Locale.ROOT)
                        .startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    options.add(settings.worldName());
                }
            }
        }
        return options;
    }
}
