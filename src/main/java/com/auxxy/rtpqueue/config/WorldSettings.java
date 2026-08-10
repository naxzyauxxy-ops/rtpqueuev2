package com.auxxy.rtpqueue.config;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Per-world queue and RTP settings loaded from config.yml.
 * MADE BY AUXXY
 */
public final class WorldSettings {

    private final String worldName;
    private final boolean enabled;
    private final String display;
    private final Material icon;
    private final int slot;
    private final String center;
    private final int minRadius;
    private final int maxRadius;

    public WorldSettings(String worldName, boolean enabled, String display, Material icon,
                         int slot, String center, int minRadius, int maxRadius) {
        this.worldName = worldName;
        this.enabled = enabled;
        this.display = display;
        this.icon = icon;
        this.slot = slot;
        this.center = center;
        this.minRadius = Math.max(0, minRadius);
        this.maxRadius = Math.max(this.minRadius + 1, maxRadius);
    }

    public String worldName() {
        return worldName;
    }

    public boolean enabled() {
        return enabled;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }

    public int slot() {
        return slot;
    }

    public int minRadius() {
        return minRadius;
    }

    public int maxRadius() {
        return maxRadius;
    }

    /** Resolves the configured centre point to block coordinates. */
    public int[] centerXZ(World world) {
        if (center != null && center.contains(",")) {
            String[] parts = center.split(",");
            try {
                return new int[]{
                        (int) Double.parseDouble(parts[0].trim()),
                        (int) Double.parseDouble(parts[1].trim())
                };
            } catch (NumberFormatException ignored) {
                // fall through to spawn
            }
        }
        Location spawn = world.getSpawnLocation();
        return new int[]{spawn.getBlockX(), spawn.getBlockZ()};
    }
}
