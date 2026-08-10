package com.auxxy.rtpqueue.config;

import com.auxxy.rtpqueue.RTPQueuePlugin;
import com.auxxy.rtpqueue.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and formats messages.yml.
 * MADE BY AUXXY
 */
public final class Messages {

    private final RTPQueuePlugin plugin;
    private FileConfiguration config;
    private String prefix = "";

    public Messages(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)));
                config.options().copyDefaults(true);
                config.save(file);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not update messages.yml: " + exception.getMessage());
        }

        prefix = Text.color(config.getString("prefix", ""));
    }

    public String prefix() {
        return prefix;
    }

    /** Coloured, placeholder-filled single line (without prefix). */
    public String raw(String path, Object... placeholders) {
        String value = config.getString(path);
        if (value == null) {
            return Text.color("&cMissing message: &f" + path);
        }
        return Text.color(Text.placeholders(value, placeholders));
    }

    public List<String> rawList(String path, Object... placeholders) {
        List<String> lines = new ArrayList<>();
        if (config.isList(path)) {
            for (String line : config.getStringList(path)) {
                lines.add(Text.color(Text.placeholders(line, placeholders)));
            }
        } else if (config.isString(path)) {
            lines.add(raw(path, placeholders));
        } else {
            lines.add(Text.color("&cMissing message: &f" + path));
        }
        return lines;
    }

    /** Sends a message (or list of messages) with the prefix applied. */
    public void send(CommandSender target, String path, Object... placeholders) {
        if (target == null) {
            return;
        }
        for (String line : rawList(path, placeholders)) {
            if (line.isEmpty()) {
                continue;
            }
            target.sendMessage(prefix + line);
        }
    }

    /** Sends without the prefix - handy for banners. */
    public void sendPlain(CommandSender target, String path, Object... placeholders) {
        if (target == null) {
            return;
        }
        for (String line : rawList(path, placeholders)) {
            target.sendMessage(line);
        }
    }

    public void title(Player player, String titlePath, String subtitlePath, Object... placeholders) {
        String title = titlePath == null ? "" : raw(titlePath, placeholders);
        String subtitle = subtitlePath == null ? "" : raw(subtitlePath, placeholders);
        player.sendTitle(title, subtitle, 0, 30, 10);
    }
}
