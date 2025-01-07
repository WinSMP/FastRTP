package org.winlogon.simplertp;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.RegionAccessor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;

/**
 * SimpleRTP is a plugin for Bukkit that allows players to teleport
 * to random safe locations within a specified range.
 */
public class SimpleRtp extends JavaPlugin {
    private RegionScheduler scheduler = Bukkit.getRegionScheduler();
    private static final Set<Material> UNSAFE_BLOCKS = EnumSet.of(
            Material.LAVA, Material.WATER, Material.FIRE, Material.CACTUS, Material.MAGMA_BLOCK);

    private int minRange;
    private int maxAttempts;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        this.getCommand("rtp").setExecutor(this);
        getLogger().info("§aSimpleRTP has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("§cSimpleRTP has been disabled!");
    }

    /**
     * Loads the plugin configuration.
     */
    private void loadConfig() {
        config = getConfig();
        minRange = config.getInt("min-range", 3000);
        maxAttempts = config.getInt("max-attempts", 50);
    }

    /**
     * Returns the maximum range for random location generation.
     * The range is clamped between the minimum range and the default maximum range.
     *
     * @param world The world to get the maximum range for
     * @return The maximum range for random location generation
     */
    private double getMaxRange(World world) {
        WorldBorder border = world.getWorldBorder();
        double defaultMaxRange = border.getSize() / 2;

        config = getConfig();
        double configMaxRange = config.getDouble("max-range", defaultMaxRange);

        // Clamp the range between minRange and defaultMaxRange
        if (configMaxRange < minRange || configMaxRange > defaultMaxRange) {
            getLogger().warning("§7Configured max-range (§3" + configMaxRange + "§7) for world §2"
                + world.getName() + "§7 is out of bounds. Clamping to valid range.");
            configMaxRange = Math.max(minRange, Math.min(configMaxRange, defaultMaxRange));
        }

        return configMaxRange;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cError§7: Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        World world = player.getWorld();
        double maxRange = getMaxRange(world);

        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            player.sendMessage("§aUsage§7: /rtp");
            player.sendMessage("§7Randomly teleports you to a safe location between §3" + minRange
                + "§7 and §3" + maxRange + "§7 blocks.");
            return true;
        }

        player.sendMessage("§7Finding a safe location to teleport you to...");
        Location safeLocation = findSafeLocation(world, maxRange);

        if (safeLocation != null) {
            player.teleport(safeLocation);
            player.sendMessage("§7You have been teleported to a random location §3successfully§7.");
        } else {
            player.sendMessage("§cError§7: Failed to find a safe location. Please try again.");
        }

        return true;
    }

    /**
     * Finds a random safe location within the specified range.
     *
     * @param world The world to search for safe locations
     * @param maxRange The maximum range to search for safe locations
     * @return A random safe location within the range, or null if no safe location is found
     */
    private Location findSafeLocation(World world, double maxRange) {
        Random random = new Random();

        for (int attempts = 0; attempts < maxAttempts; attempts++) {
            int x = random.nextInt((int) maxRange * 2) - (int) maxRange;
            int z = random.nextInt((int) maxRange * 2) - (int) maxRange;

            // Ensure the location is within the border
            WorldBorder border = world.getWorldBorder();
            int y = 0;
            if (isFolia()) {
                y = getHighestBlockYAtAsync(x, z, world);
            } else {
                y = world.getHighestBlockYAt(x, z);
            }
            
            if (!border.isInside(new Location(world, x, y, z))) {
                continue;
            }

            // Check if within the minimum range from spawn
            Location spawn = world.getSpawnLocation();
            Location randomLocation = new Location(world, x, spawn.getY(), z);

            if (spawn.distanceSquared(randomLocation) < minRange * minRange) {
                continue;
            }

            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                if (isFolia()) {
                    scheduler.execute(this, randomLocation, () -> world.loadChunk(chunkX, chunkZ));
                } else {
                    world.loadChunk(chunkX, chunkZ);
                }
            }

            int highestY = getHighestBlockYAtAsync(x, z, world);
            Location potentialLocation = new Location(world, x + 0.5, highestY + 1, z + 0.5);

            Material blockBelow, blockAtFeet, blockAtHead;
            if (!isFolia()) {
                blockBelow = world.getBlockAt(x, highestY - 1, z).getType();
                blockAtFeet = world.getBlockAt(x, highestY, z).getType();
                blockAtHead = world.getBlockAt(x, highestY + 1, z).getType();
            } else {
                blockBelow = getBlockAtAsync(new Location(world, x, highestY, z));
                blockAtFeet = getBlockAtAsync(new Location(world, x, highestY + 1, z));
                blockAtHead = getBlockAtAsync(new Location(world, x, highestY + 2, z));
            }

            if (isSafeBlock(blockBelow) && blockAtFeet == Material.AIR
                && blockAtHead == Material.AIR) {
                return potentialLocation;
            }
        }
        return null; // No safe location found within max attempts
    }

    /**
     * Checks if a block is safe to teleport to.
     *
     * @param material The material of the block
     * @return True if the block is safe, false otherwise
     * @see UNSAFE_BLOCKS
     */
    private boolean isSafeBlock(Material material) {
        return material.isSolid() && !UNSAFE_BLOCKS.contains(material);
    }

    /**
     * Folia-specific async wrapper for World#getHighestBlockYAt which uses
     * the RegionScheduler to load the chunk.
     * 
     * @param x The x-coordinate of the block
     * @param z The z-coordinate of the block
     * @param world The world of the block
     * @return The highest y-coordinate of the block
     */
    private int getHighestBlockYAtAsync(int x, int z, World world) {
        // RegionScheduler scheduler = Bukkit.getRegionScheduler();

        AtomicInteger y = new AtomicInteger();
        RegionAccessor regionAccessor = (RegionAccessor) world;

        scheduler.execute(this, new Location(world, x, 0, z), () -> {
            y.set(regionAccessor.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING));
        });
        return y.get();
    }

    /**
     * Folia-specific async wrapper for World#getBlockAt which uses
     * the RegionScheduler to load the chunk.
     * 
     * @param Location The location of the block
     * @return Material The material of the block
     */
    private Material getBlockAtAsync(Location loc) {
        final Location location = loc;
        World world = location.getWorld();
        
        // RegionScheduler scheduler = Bukkit.getRegionScheduler();
        ConcurrentWrapper<Material> materialWrapper = new ConcurrentWrapper<>(Material.AIR);

        scheduler.execute(this, loc, () -> {
            int x = (int) location.getX();
            int y = (int) location.getY();
            int z = (int) location.getZ();
            materialWrapper.set(world.getBlockAt(x, y, z).getType());
        });

        return materialWrapper.get();
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
