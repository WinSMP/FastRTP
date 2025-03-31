package org.winlogon.simplertp;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;

/**
 * SimpleRTP, a simple plugin for teleporting players to a random safe location.
 * 
 * @author walker84837
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
        registerCommands();
        getLogger().info("SimpleRTP has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleRTP has been disabled!");
    }

    private void loadConfig() {
        config = getConfig();
        minRange = config.getInt("min-range", 3000);
        maxAttempts = config.getInt("max-attempts", 50);
    }

    private void registerCommands() {
        new CommandAPICommand("rtp")
                .withAliases("randomtp")
                .withOptionalArguments(new StringArgument("option"))
                .executesPlayer((player, args) -> {
                    String option = (String) args.getOrDefault("option", "");
                    World world = player.getWorld();
                    double maxRange = getMaxRange(world);

                    if ("help".equalsIgnoreCase(option)) {
                        player.sendMessage("§aUsage§7: /rtp");
                        player.sendMessage("§7Teleports you to a safe location between §3"
                                + minRange + "§7 and §3" + maxRange + "§7 blocks.");
                        return;
                    }

                    player.sendMessage("§7Finding a safe location...");
                    if (isFolia()) {
                        findSafeLocationAsync(world, maxRange, 0, safeLoc -> {
                            if (safeLoc != null) {
                                player.getScheduler().run(this, task -> {
                                    player.teleportAsync(safeLoc);
                                    player.sendMessage("§7Teleported §3successfully§7!");
                                }, null);
                            } else {
                                player.sendMessage("§cError§7: No safe location found.");
                            }
                        });
                    } else {
                        Location safeLoc = findSafeLocationSync(world, maxRange);
                        if (safeLoc != null) {
                            player.teleport(safeLoc);
                            player.sendMessage("§7Teleported §3successfully§7!");
                        } else {
                            player.sendMessage("§cError§7: No safe location found.");
                        }
                    }
                })
                .register();
    }

    private double getMaxRange(World world) {
        WorldBorder border = world.getWorldBorder();
        double defaultMaxRange = border.getSize() / 2;
        double configMaxRange = config.getDouble("max-range", defaultMaxRange);

        return Math.max(minRange, Math.min(configMaxRange, defaultMaxRange));
    }

    private void findSafeLocationAsync(
            World world, double maxRange, int attempt, Consumer<Location> callback
    ) {
        if (attempt >= maxAttempts) {
            callback.accept(null);
            return;
        }

        Random random = new Random();
        int x = random.nextInt((int) maxRange * 2) - (int) maxRange;
        int z = random.nextInt((int) maxRange * 2) - (int) maxRange;
        Location loc = new Location(world, x, 0, z);

        if (!isWithinWorldBorder(world, loc) || !isOutsideMinRange(world, x, z)) {
            findSafeLocationAsync(world, maxRange, attempt + 1, callback);
            return;
        }

        scheduler.execute(this, loc, () -> {
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                int y = findSafeY(world, x, z);
                if (y != -1) {
                    callback.accept(new Location(world, x + 0.5, y + 1, z + 0.5));
                } else {
                    findSafeLocationAsync(world, maxRange, attempt + 1, callback);
                }
            } else {
                world.getChunkAtAsync(chunkX, chunkZ, true, true, chunk -> {
                    if (!chunk.isLoaded()) {
                        findSafeLocationAsync(world, maxRange, attempt + 1, callback);
                        return;
                    }
                    int y = findSafeY(world, x, z);
                    if (y != -1) {
                        callback.accept(new Location(world, x + 0.5, y + 1, z + 0.5));
                    } else {
                        findSafeLocationAsync(world, maxRange, attempt + 1, callback);
                    }
                });
            }
        });
    }

    private Location findSafeLocationSync(World world, double maxRange) {
        Random random = new Random();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = random.nextInt((int) maxRange * 2) - (int) maxRange;
            int z = random.nextInt((int) maxRange * 2) - (int) maxRange;
            Location loc = new Location(world, x, 0, z);

            if (!isWithinWorldBorder(world, loc) || !isOutsideMinRange(world, x, z)) {
                continue;
            }

            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ);
            }

            int y = findSafeY(world, x, z);
            if (y != -1) return new Location(world, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    private int findSafeY(World world, int x, int z) {
        int low = world.getMinHeight();
        int high = world.getMaxHeight();
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

    private boolean isWithinWorldBorder(World world, Location loc) {
        return world.getWorldBorder().isInside(loc);
    }

    private boolean isOutsideMinRange(World world, int x, int z) {
        Location spawn = world.getSpawnLocation();
        return spawn.distanceSquared(new Location(world, x, spawn.getY(), z)) >= minRange * minRange;
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
