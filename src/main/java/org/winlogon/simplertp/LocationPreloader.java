package org.winlogon.simplertp;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.winlogon.asynccraftr.AsyncCraftr.Task;
import org.winlogon.asynccraftr.AsyncCraftr;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class LocationPreloader {
    private static final Set<Material> UNSAFE_BLOCKS = EnumSet.of(
        Material.LAVA, Material.WATER, Material.FIRE, Material.CACTUS, Material.MAGMA_BLOCK
    );
    private final SimpleRtp plugin;
    private Task task;

    private final int maxPoolSize;
    private final LocationPool pool;
    private final Logger logger;
    private final RtpConfig config;

    public LocationPreloader(SimpleRtp plugin, Logger logger) {
        this.plugin = plugin;
        this.config = plugin.getRtpConfig();
        this.logger = logger;
        this.maxPoolSize = config.maxPoolSize();

        this.pool = new LocationPool(maxPoolSize);
    }

    /** Kick off the repeating generation task. */
    public void start() {
        logger.info("Starting location preloader service");
        var configInterval = plugin.getConfig().getInt("preload-interval-hours", 1);
        var interval = Duration.ofHours(configInterval);
        task = AsyncCraftr.runAsyncTaskTimer(plugin, this::generateSafeLocations, Duration.ZERO, interval);
    }

    /** Stop the repeating task. */
    public void stop() {
        logger.info("Stopping location preloader");
        if (task != null) {
            task.cancel();
        }
    }

    /** Poll a preloaded location, if available. */
    public Optional<Location> nextPreloadedLocation() {
        return Optional.ofNullable(pool.poll());
    }

    private void generateSafeLocations() {
        World world = plugin.getServer().getWorlds().get(0);
        int maxChunkAttempts = plugin.getConfig().getInt("max-chunk-attempts", 20);
        int toFind = plugin.getConfig().getInt("locations-per-hour", 5);
        int perChunkLimit = Math.max(2, toFind / maxChunkAttempts);


        logger.info("Starting preload");
        logger.info(STR."I will try to find \{perChunkLimit} locations in \{maxChunkAttempts} chunks, with \{toFind} samples per chunk");

        var attempts = new AtomicInteger(0);
        var found = new AtomicInteger(0);

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (int i = 0; i < maxChunkAttempts; i++) {
            chain = chain.thenCompose(unused -> {
                if (found.get() >= toFind) {
                    return CompletableFuture.completedFuture(null);
                }

                int maxRange = (int) Math.round(config.getMaxRange(world));
                int chunkX = ThreadLocalRandom.current().nextInt(maxRange * 2) - maxRange;
                int chunkZ = ThreadLocalRandom.current().nextInt(maxRange * 2) - maxRange;

                return world.getChunkAtAsync(chunkX, chunkZ, true).thenAccept(chunk -> {
                    attempts.incrementAndGet();
                    var snap = chunk.getChunkSnapshot();
                    for (int s = 0; s < perChunkLimit && found.get() < toFind; s++) {
                        int localX = ThreadLocalRandom.current().nextInt(16);
                        int localZ = ThreadLocalRandom.current().nextInt(16);
                        int x = (chunk.getX() << 4) + localX;
                        int z = (chunk.getZ() << 4) + localZ;
                        if (!isValidChunkSample(world, snap, x, z)) continue;
                        Location loc = new Location(world, x + .5, snap.getHighestBlockYAt(localX, localZ) + 1, z + .5);
                        pool.offer(loc);
                        found.incrementAndGet();
                    }
                }).exceptionally(ex -> {
                    logger.warning("Async chunk load failed: " + ex);
                    return null;
                });
            });
        }

        chain.whenComplete((v, ex) -> {
            logger.info("Preload cycle done: attempted " + attempts.get()
                    + " chunks, found " + found.get() + " locations.");
        });
    }

    private boolean isValidChunkSample(World world, ChunkSnapshot snap, int x, int z) {
        Location location = new Location(world, x, 0, z);

        var spawnLocation = world.getSpawnLocation();
        var minRange = plugin.getConfig().getInt("min-range", 3000);
        var distanceFromSpawn = spawnLocation.distanceSquared(new Location(world, x, spawnLocation.getY(), z));

        if (!world.getWorldBorder().isInside(location) || distanceFromSpawn < Math.pow(minRange, 2)) {
            return false;
        }

        int localX = x & 0xF, localZ = z & 0xF;
        int y = snap.getHighestBlockYAt(localX, localZ);

        if (y < world.getMinHeight() || y >= world.getMaxHeight() - 2) return false;

        Material below = snap.getBlockType(localX, y - 1, localZ);
        Material foot = snap.getBlockType(localX, y + 1, localZ);
        Material head = snap.getBlockType(localX, y + 2, localZ);

        return foot == Material.AIR
            && head == Material.AIR
            && !LocationPreloader.UNSAFE_BLOCKS.contains(below);
    }
}
