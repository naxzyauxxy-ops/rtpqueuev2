package com.auxxy.rtpqueue.rtp;

import com.auxxy.rtpqueue.RTPQueuePlugin;
import com.auxxy.rtpqueue.config.PluginConfig;
import com.auxxy.rtpqueue.config.WorldSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Finds a safe random landing spot, loading chunks asynchronously so the
 * main thread is never blocked by terrain generation.
 * MADE BY AUXXY
 */
public final class SafeLocationFinder {

    private final RTPQueuePlugin plugin;

    public SafeLocationFinder(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @return a future completing with a safe location, or null when the
     *         search ran out of attempts.
     */
    public CompletableFuture<Location> find(World world, WorldSettings settings) {
        CompletableFuture<Location> future = new CompletableFuture<>();
        attempt(world, settings, future, 0);
        return future;
    }

    private void attempt(World world, WorldSettings settings,
                         CompletableFuture<Location> future, int tries) {
        if (tries >= plugin.config().maxAttempts()) {
            future.complete(null);
            return;
        }

        int[] center = settings.centerXZ(world);
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0D;
        double distance = settings.minRadius()
                + ThreadLocalRandom.current().nextDouble()
                * (settings.maxRadius() - settings.minRadius());

        int x = center[0] + (int) Math.round(Math.cos(angle) * distance);
        int z = center[1] + (int) Math.round(Math.sin(angle) * distance);

        if (plugin.config().useWorldBorder() && !insideBorder(world, x, z)) {
            attempt(world, settings, future, tries + 1);
            return;
        }

        loadChunk(world, x, z).thenRun(() -> {
            Location safe = scan(world, x, z);
            if (safe != null) {
                future.complete(safe);
            } else {
                attempt(world, settings, future, tries + 1);
            }
        });
    }

    /** Looks for a safe spot near the given coordinates, spiralling outwards. */
    public CompletableFuture<Location> findNear(World world, int x, int z, int radius) {
        CompletableFuture<Location> future = new CompletableFuture<>();
        nearAttempt(world, x, z, radius, future, 0);
        return future;
    }

    private void nearAttempt(World world, int x, int z, int radius,
                             CompletableFuture<Location> future, int tries) {
        if (tries >= 12) {
            future.complete(null);
            return;
        }
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0D;
        int offsetX = x + (int) Math.round(Math.cos(angle) * radius);
        int offsetZ = z + (int) Math.round(Math.sin(angle) * radius);

        if (plugin.config().useWorldBorder() && !insideBorder(world, offsetX, offsetZ)) {
            nearAttempt(world, x, z, radius, future, tries + 1);
            return;
        }

        loadChunk(world, offsetX, offsetZ).thenRun(() -> {
            Location safe = scan(world, offsetX, offsetZ);
            if (safe != null) {
                future.complete(safe);
            } else {
                nearAttempt(world, x, z, radius, future, tries + 1);
            }
        });
    }

    /** Loads the chunk off-thread, then hands control back to the main thread. */
    private CompletableFuture<Void> loadChunk(World world, int blockX, int blockZ) {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        world.getChunkAtAsync(blockX >> 4, blockZ >> 4).whenComplete((chunk, error) -> {
            if (error != null) {
                plugin.debug("Chunk load failed: " + error.getMessage());
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> ready.complete(null));
            } else {
                ready.complete(null);
            }
        });
        return ready;
    }

    private boolean insideBorder(World world, int x, int z) {
        WorldBorder border = world.getWorldBorder();
        double size = border.getSize() / 2.0D - 8.0D;
        Location center = border.getCenter();
        return Math.abs(x - center.getX()) <= size && Math.abs(z - center.getZ()) <= size;
    }

    /** Must run on the main thread with the chunk loaded. */
    private Location scan(World world, int x, int z) {
        PluginConfig config = plugin.config();
        int minY = Math.max(world.getMinHeight() + 1, config.minY());
        int maxY = Math.min(world.getMaxHeight() - 3, config.maxY());
        if (minY >= maxY) {
            return null;
        }

        if (hasCeiling(world)) {
            for (int y = maxY; y >= minY; y--) {
                Location candidate = check(world, x, y, z);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }

        int surfaceY = world.getHighestBlockYAt(x, z);
        if (surfaceY < minY || surfaceY > maxY) {
            return null;
        }
        return check(world, x, surfaceY, z);
    }

    private boolean hasCeiling(World world) {
        return world.getEnvironment() == World.Environment.NETHER;
    }

    /** Checks a ground block at (x, y, z) plus the two blocks above it. */
    private Location check(World world, int x, int y, int z) {
        Block ground = world.getBlockAt(x, y, z);
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);

        if (!ground.getType().isSolid()) {
            return null;
        }
        if (isUnsafe(ground.getType()) || isUnsafe(feet.getType()) || isUnsafe(head.getType())) {
            return null;
        }
        if (!isPassable(feet) || !isPassable(head)) {
            return null;
        }
        if (isBlockedBiome(world, x, y, z)) {
            return null;
        }
        return new Location(world, x + 0.5D, y + 1.0D, z + 0.5D);
    }

    private boolean isPassable(Block block) {
        Material type = block.getType();
        return type == Material.AIR || type == Material.CAVE_AIR || block.isPassable();
    }

    private boolean isUnsafe(Material material) {
        return plugin.config().unsafeBlocks().contains(material);
    }

    private boolean isBlockedBiome(World world, int x, int y, int z) {
        if (plugin.config().blockedBiomes().isEmpty()) {
            return false;
        }
        Biome biome = world.getBiome(x, y, z);
        String key;
        try {
            key = biome.getKey().getKey();
        } catch (Throwable ignored) {
            key = biome.toString();
        }
        return plugin.config().blockedBiomes().contains(key.toUpperCase(Locale.ROOT));
    }
}
