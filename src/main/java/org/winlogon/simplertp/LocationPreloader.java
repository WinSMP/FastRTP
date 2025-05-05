package org.winlogon.simplertp;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.EnumSet;
import java.util.Set;

public class LocationPreloader {
    private static final Set<Material> UNSAFE_BLOCKS = EnumSet.of(
        Material.LAVA, Material.WATER, Material.FIRE, Material.CACTUS, Material.MAGMA_BLOCK
    );
    private final Plugin plugin;
    private final LocationPool pool = new LocationPool();

    private final boolean isFolia;
    private final GlobalRegionScheduler globalScheduler; // only non-null on Folia
    private final BukkitScheduler bukkitScheduler; // only non-null off-Folia

    private BukkitTask bukkitTask;

    public LocationPreloader(Plugin plugin) {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            this.globalScheduler = Bukkit.getGlobalRegionScheduler(); // Now using GlobalRegionScheduler
            this.isFolia = true;
        } catch (ClassNotFoundException e) {
            this.isFolia = false;
        }
    }

    /** Kick off the repeating generation task. */
    public void start() {
        var intervalHours = plugin.getConfig().getInt("preload-interval-hours", 1);
        if (isFolia) {
            // Convert hours to ticks (20 ticks/sec * 3600 sec/hr)
            long periodTicks = intervalHours * 72000L;
            globalScheduler.runAtFixedRate(
                plugin,
                task -> generateSomeSafeLocations(),
                0,
                periodTicks
            );
        } else {
            // Run on main thread to avoid concurrency issues
            long ticks = TimeUnit.HOURS.toSeconds(intervalHours) * 20;
            bukkitTask = bukkitScheduler.runTaskTimer(
                plugin,
                this::generateSomeSafeLocations,
                0L,
                ticks
            );
        }
    }

    /** Stop the repeating task. */
    public void stop() {
        if (!isFolia && bukkitTask != null) {
            bukkitTask.cancel();
        }
    }

    /** Poll a preloaded location, if available. */
    public Optional<Location> nextPreloadedLocation() {
        return Optional.ofNullable(pool.poll());
    }

    private void generateSomeSafeLocations() {
        World world = plugin.getServer().getWorlds().get(0);
        int attempts = 0, found = 0;
        int maxAttempts = plugin.getConfig().getInt("max-attempts-per-hour", 20);
        int toFind     = plugin.getConfig().getInt("locations-per-hour", 5);

        while (attempts++ < maxAttempts && found < toFind) {
            Location loc = pickRandomXZ(world);
            if (!isWithinWorldBorder(world, loc) || !isOutsideMinRange(world, loc)) {
                continue;
            }

            int y = findSafeY(world, loc.getBlockX(), loc.getBlockZ());
            if (y != -1) {
                loc.setY(y + 1);
                loc.setX(loc.getBlockX() + 0.5);
                loc.setZ(loc.getBlockZ() + 0.5);
                pool.offer(loc);
                found++;
            }
        }
    }

    private Location pickRandomXZ(World world) {
        int maxRange = (int) getMaxRange(world, plugin.getConfig(), 3000);
        int x = ThreadLocalRandom.current().nextInt(maxRange * 2) - maxRange;
        int z = ThreadLocalRandom.current().nextInt(maxRange * 2) - maxRange;
        return new Location(world, x, 0, z);
    }

    private double getMaxRange(World world, FileConfiguration config, int minRange) {
        WorldBorder border = world.getWorldBorder();
        double defaultMaxRange = border.getSize() / 2;
        double configMaxRange = config.getDouble("max-range", defaultMaxRange);
        return Math.max(minRange, Math.min(configMaxRange, defaultMaxRange));
    }

    private boolean isWithinWorldBorder(World w, Location loc) {
        return w.getWorldBorder().isInside(loc);
    }

    private boolean isOutsideMinRange(World w, Location loc) {
        Location spawn = w.getSpawnLocation();
        double minRange = plugin.getConfig().getDouble("min-range", 3000);
        return spawn.distanceSquared(
            new Location(w, loc.getBlockX(), spawn.getY(), loc.getBlockZ())
        ) >= minRange * minRange;
    }

    private static int findSafeY(World world, int x, int z) {
        int minHeight = world.getMinHeight();
        int high = world.getMaxHeight();
        int low = (int) (minHeight + (high - minHeight) / 3.75);
        int highestSolidY = -1;
        
        while (low <= high) {
            int mid = (low + high) / 2;
            Material block = world.getBlockAt(x, mid, z).getType();
            
            if (block.isSolid()) {
                highestSolidY = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        
        if (highestSolidY == -1) {
            return -1;
        }
        
        Material above1 = world.getBlockAt(x, highestSolidY + 1, z).getType();
        Material above2 = world.getBlockAt(x, highestSolidY + 2, z).getType();
        if (above1 != Material.AIR || above2 != Material.AIR) {
            return -1;
        }
        
        Material below = world.getBlockAt(x, highestSolidY - 1, z).getType();
        return UNSAFE_BLOCKS.contains(below) ? -1 : highestSolidY;
    }
}

