package com.auxxy.rtpqueue;

import com.auxxy.rtpqueue.command.QueueCommand;
import com.auxxy.rtpqueue.config.Messages;
import com.auxxy.rtpqueue.config.PluginConfig;
import com.auxxy.rtpqueue.gui.QueueMenu;
import com.auxxy.rtpqueue.license.LicenseClient;
import com.auxxy.rtpqueue.listener.MenuListener;
import com.auxxy.rtpqueue.listener.PlayerListener;
import com.auxxy.rtpqueue.match.MatchManager;
import com.auxxy.rtpqueue.queue.QueueManager;
import com.auxxy.rtpqueue.rtp.SafeLocationFinder;
import com.auxxy.rtpqueue.util.Cooldowns;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RTPQueue - random-teleport matchmaking queue.
 *
 * MADE BY AUXXY
 */
public final class RTPQueuePlugin extends JavaPlugin {

    /** Credit string used across the plugin. */
    public static final String MADE_BY = "MADE BY AUXXY";

    private PluginConfig config;
    private Messages messages;
    private SafeLocationFinder locations;
    private QueueManager queues;
    private MatchManager matches;
    private QueueMenu queueMenu;
    private LicenseClient license;
    private final Cooldowns queueCooldowns = new Cooldowns();

    @Override
    public void onEnable() {
        config = new PluginConfig(this);
        config.load();

        messages = new Messages(this);
        messages.load();

        // Licensing starts before anything else so an invalid key stops the
        // plugin early. The check itself runs off-thread.
        license = new LicenseClient(this);
        license.start();

        locations = new SafeLocationFinder(this);
        matches = new MatchManager(this);
        queues = new QueueManager(this);
        queueMenu = new QueueMenu(this);

        registerCommands();
        registerListeners();

        queues.start();

        banner();
    }

    /** Licensing client; never null once onEnable has run. */
    public LicenseClient license() {
        return license;
    }

    @Override
    public void onDisable() {
        if (queues != null) {
            queues.stop();
            queues.clear();
        }
        if (matches != null) {
            matches.shutdown();
        }
        if (license != null) {
            license.stop();
        }
        queueCooldowns.clearAll();
        getLogger().info("RTPQueue disabled. " + MADE_BY);
    }

    private void banner() {
        String credit = config.madeBy() == null || config.madeBy().isBlank()
                ? MADE_BY
                : config.madeBy();
        getLogger().info("=====================================");
        getLogger().info("  RTPQueue v" + version());
        getLogger().info("  " + credit);
        getLogger().info("=====================================");
    }

    private void registerCommands() {
        QueueCommand queueCommand = new QueueCommand(this);
        bind("rtpqueue", queueCommand, queueCommand);
    }

    private void bind(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
    }

    /** Reloads config.yml and messages.yml and restarts the schedulers. */
    public void reloadEverything() {
        config.load();
        messages.load();
        queues.start();
    }

    public String version() {
        try {
            return getPluginMeta().getVersion();
        } catch (Throwable ignored) {
            return getDescription().getVersion();
        }
    }

    public void debug(String message) {
        if (config != null && config.debug()) {
            getLogger().info("[debug] " + message);
        }
    }

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public SafeLocationFinder locations() {
        return locations;
    }

    public QueueManager queues() {
        return queues;
    }

    public MatchManager matches() {
        return matches;
    }

    public QueueMenu queueMenu() {
        return queueMenu;
    }

    public Cooldowns queueCooldowns() {
        return queueCooldowns;
    }
}
