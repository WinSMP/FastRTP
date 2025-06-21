package org.winlogon.simplertp;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
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
    private final SimpleRtp plugin;

    private boolean isFolia;
    private GlobalRegionScheduler globalScheduler = null;
    private final BukkitScheduler bukkitScheduler;

    private final int maxPoolSize;
    private final LocationPool pool;

    private BukkitTask bukkitTask;

    public LocationPreloader(SimpleRtp plugin) {
        var config = plugin.getRtpConfig();

        this.plugin = plugin;
        this.maxPoolSize = config.maxPoolSize();
        this.bukkitScheduler = plugin.getServer().getScheduler();

        this.pool = new LocationPool(maxPoolSize);
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            globalScheduler = Bukkit.getGlobalRegionScheduler();
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
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
                periodTicks,
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
        int chunkAttempts = 0;
        int found = 0;
        int maxChunkAttempts = plugin.getConfig().getInt("max-chunk-attempts", 20);
        int toFind = plugin.getConfig().getInt("locations-per-hour", 5);
        int samplesPerChunk = plugin.getRtpConfig().samplesPerChunk();

        while (chunkAttempts++ < maxChunkAttempts && found < toFind) {
            Location chunkLoc = pickRandomXZ(world);
            int chunkX = chunkLoc.getBlockX() >> 4;
            int chunkZ = chunkLoc.getBlockZ() >> 4;
            
            // Load chunk
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ);
            }
            var chunk = world.getChunkAt(chunkX, chunkZ);
            var snap = chunk.getChunkSnapshot();
            
            // Sample multiple locations in chunk
            for (int i = 0; i < samplesPerChunk && found < toFind; i++) {
                int localX = ThreadLocalRandom.current().nextInt(16);
                int localZ = ThreadLocalRandom.current().nextInt(16);
                int x = (chunkX << 4) + localX;
                int z = (chunkZ << 4) + localZ;
                Location loc = new Location(world, x, 0, z);
                
                if (!isWithinWorldBorder(world, loc) || !isOutsideMinRange(world, loc)) {
                    continue;
                }
                
                int highestY = snap.getHighestBlockYAt(localX, localZ);
                if (highestY < world.getMinHeight() || highestY >= world.getMaxHeight() - 2) continue;
                
                var above1 = snap.getBlockType(localX, highestY + 1, localZ);
                var above2 = snap.getBlockType(localX, highestY + 2, localZ);
                var below = snap.getBlockType(localX, highestY - 1, localZ);
                
                if (above1 == Material.AIR && 
                    above2 == Material.AIR && 
                    !UNSAFE_BLOCKS.contains(below)) {
                    
                    loc.setY(highestY + 1);
                    loc.setX(loc.getBlockX() + 0.5);
                    loc.setZ(loc.getBlockZ() + 0.5);
                    pool.offer(loc);
                    found++;
                }
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
        var border = world.getWorldBorder();
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

