package com.auxxy.rtpqueue.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small colour / placeholder helper.
 * MADE BY AUXXY
 */
public final class Text {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    /** Translates &amp; codes and &amp;#RRGGBB hex codes. */
    public static String color(String input) {
        if (input == null) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char c : matcher.group(1).toCharArray()) {
                replacement.append('\u00A7').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static List<String> color(List<String> input) {
        List<String> out = new ArrayList<>();
        if (input == null) {
            return out;
        }
        for (String line : input) {
            out.add(color(line));
        }
        return out;
    }

    public static String strip(String input) {
        return ChatColor.stripColor(color(input));
    }

    /**
     * Replaces %key% placeholders. Arguments are given in key, value pairs.
     */
    public static String placeholders(String input, Object... pairs) {
        if (input == null) {
            return "";
        }
        String result = input;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace("%" + pairs[i] + "%", String.valueOf(pairs[i + 1]));
        }
        return result;
    }

    /** Formats seconds as e.g. 1m 5s. */
    public static String time(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long rest = seconds % 60;
        return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
    }
}
