package com.auxxy.rtpqueue.config;

import com.auxxy.rtpqueue.RTPQueuePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Typed view over config.yml.
 * MADE BY AUXXY
 */
public final class PluginConfig {

    private final RTPQueuePlugin plugin;

    private String madeBy = "MADE BY AUXXY";
    private boolean debug;

    private int matchmakingIntervalTicks = 20;
    private boolean leaveOnWorldChange = true;
    private boolean announceQueueUpdates = true;

    private int cooldownSeconds = 60;

    private int countdownSeconds = 5;
    private int graceSeconds = 2;
    private int timeoutSeconds = 300;
    private int spread = 14;
    private boolean returnToOrigin = true;
    private int returnDelaySeconds = 5;
    private int leashBlocks = 200;
    private boolean menuEnabled = true;
    private String defaultWorld = "";
    private boolean healOnStart = true;
    private boolean feedOnStart = true;
    private boolean clearEffectsOnStart;
    private boolean extinguishOnStart = true;
    private List<String> winnerCommands = new ArrayList<>();
    private List<String> loserCommands = new ArrayList<>();

    private int maxAttempts = 45;
    private boolean useWorldBorder = true;
    private int minY = 45;
    private int maxY = 200;
    private Set<Material> unsafeBlocks = EnumSet.noneOf(Material.class);
    private Set<String> blockedBiomes = new HashSet<>();

    private final Map<String, WorldSettings> worlds = new LinkedHashMap<>();

    private String queueTitle = "&8Queue &7| &bMADE BY AUXXY";
    private int queueRows = 3;
    private Material queueFiller = Material.BLACK_STAINED_GLASS_PANE;
    private int queueLeaveSlot = 22;
    private Material queueLeaveIcon = Material.BARRIER;


    public PluginConfig(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        madeBy = cfg.getString("made-by", "MADE BY AUXXY");
        debug = cfg.getBoolean("debug", false);

        matchmakingIntervalTicks = Math.max(5, cfg.getInt("queue.matchmaking-interval-ticks", 20));
        leaveOnWorldChange = cfg.getBoolean("queue.leave-on-world-change", true);
        announceQueueUpdates = cfg.getBoolean("queue.announce-queue-updates", true);

        cooldownSeconds = Math.max(0, cfg.getInt("cooldown.seconds", 60));

        countdownSeconds = Math.max(0, cfg.getInt("match.countdown-seconds", 5));
        graceSeconds = Math.max(0, cfg.getInt("match.grace-seconds", 2));
        timeoutSeconds = Math.max(0, cfg.getInt("match.timeout-seconds", 300));
        spread = Math.max(2, cfg.getInt("match.spread", 14));
        returnToOrigin = cfg.getBoolean("match.return-to-origin", true);
        returnDelaySeconds = Math.max(0, cfg.getInt("match.return-delay-seconds", 5));
        // 0 disables the leash entirely.
        leashBlocks = Math.max(0, cfg.getInt("match.leash-blocks", 200));
        menuEnabled = cfg.getBoolean("gui.enabled", true);
        defaultWorld = cfg.getString("gui.default-world", "");
        healOnStart = cfg.getBoolean("match.heal-on-start", true);
        feedOnStart = cfg.getBoolean("match.feed-on-start", true);
        clearEffectsOnStart = cfg.getBoolean("match.clear-effects-on-start", false);
        extinguishOnStart = cfg.getBoolean("match.extinguish-on-start", true);
        winnerCommands = cfg.getStringList("match.winner-commands");
        loserCommands = cfg.getStringList("match.loser-commands");

        maxAttempts = Math.max(5, cfg.getInt("rtp.max-attempts", 45));
        useWorldBorder = cfg.getBoolean("rtp.use-world-border", true);
        minY = cfg.getInt("rtp.min-y", 45);
        maxY = cfg.getInt("rtp.max-y", 200);

        unsafeBlocks = EnumSet.noneOf(Material.class);
        for (String raw : cfg.getStringList("rtp.unsafe-blocks")) {
            Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                warn("Unknown material in rtp.unsafe-blocks: " + raw);
                continue;
            }
            unsafeBlocks.add(material);
        }

        blockedBiomes = new HashSet<>();
        for (String raw : cfg.getStringList("rtp.blocked-biomes")) {
            blockedBiomes.add(raw.trim().toUpperCase(Locale.ROOT));
        }

        worlds.clear();
        ConfigurationSection worldSection = cfg.getConfigurationSection("worlds");
        if (worldSection != null) {
            for (String key : worldSection.getKeys(false)) {
                ConfigurationSection section = worldSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                Material icon = Material.matchMaterial(
                        section.getString("icon", "GRASS_BLOCK").toUpperCase(Locale.ROOT));
                if (icon == null || !icon.isItem()) {
                    warn("Invalid icon for world '" + key + "', falling back to GRASS_BLOCK.");
                    icon = Material.GRASS_BLOCK;
                }
                worlds.put(key, new WorldSettings(
                        key,
                        section.getBoolean("enabled", true),
                        section.getString("display", "&f" + key),
                        icon,
                        section.getInt("slot", -1),
                        section.getString("center", "spawn"),
                        section.getInt("min-radius", 500),
                        section.getInt("max-radius", 5000)));
            }
        }

        queueTitle = cfg.getString("gui.queue.title", queueTitle);
        queueRows = clampRows(cfg.getInt("gui.queue.rows", 3));
        queueFiller = material(cfg.getString("gui.queue.filler", "BLACK_STAINED_GLASS_PANE"),
                Material.BLACK_STAINED_GLASS_PANE);
        queueLeaveSlot = cfg.getInt("gui.queue.leave-slot", 22);
        queueLeaveIcon = material(cfg.getString("gui.queue.leave-icon", "BARRIER"), Material.BARRIER);
    }

    private Material material(String raw, Material fallback) {
        if (raw == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return (material == null || !material.isItem()) ? fallback : material;
    }

    private int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }

    private void warn(String message) {
        plugin.getLogger().warning(message);
    }

    public String madeBy() {
        return madeBy;
    }

    public boolean debug() {
        return debug;
    }

    public int matchmakingIntervalTicks() {
        return matchmakingIntervalTicks;
    }

    public boolean leaveOnWorldChange() {
        return leaveOnWorldChange;
    }

    public boolean announceQueueUpdates() {
        return announceQueueUpdates;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public int graceSeconds() {
        return graceSeconds;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    public int spread() {
        return spread;
    }

    public boolean returnToOrigin() {
        return returnToOrigin;
    }

    public int leashBlocks() {
        return leashBlocks;
    }

    /** When false, /rtpqueue joins the queue directly instead of opening the GUI. */
    public boolean menuEnabled() {
        return menuEnabled;
    }

    /** World used when the GUI is disabled. Blank means the first configured world. */
    public String defaultWorld() {
        return defaultWorld;
    }

    public int returnDelaySeconds() {
        return returnDelaySeconds;
    }

    public boolean healOnStart() {
        return healOnStart;
    }

    public boolean feedOnStart() {
        return feedOnStart;
    }

    public boolean clearEffectsOnStart() {
        return clearEffectsOnStart;
    }

    public boolean extinguishOnStart() {
        return extinguishOnStart;
    }

    public List<String> winnerCommands() {
        return Collections.unmodifiableList(winnerCommands);
    }

    public List<String> loserCommands() {
        return Collections.unmodifiableList(loserCommands);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public boolean useWorldBorder() {
        return useWorldBorder;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public Set<Material> unsafeBlocks() {
        return Collections.unmodifiableSet(unsafeBlocks);
    }

    public Set<String> blockedBiomes() {
        return Collections.unmodifiableSet(blockedBiomes);
    }

    public Map<String, WorldSettings> worlds() {
        return Collections.unmodifiableMap(worlds);
    }

    public WorldSettings world(String name) {
        return worlds.get(name);
    }

    public String queueTitle() {
        return queueTitle;
    }

    public int queueRows() {
        return queueRows;
    }

    public Material queueFiller() {
        return queueFiller;
    }

    public int queueLeaveSlot() {
        return queueLeaveSlot;
    }

    public Material queueLeaveIcon() {
        return queueLeaveIcon;
    }
}
