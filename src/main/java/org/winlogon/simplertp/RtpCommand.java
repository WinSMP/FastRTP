package org.winlogon.simplertp;

import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Default;
import dev.jorel.commandapi.annotations.Help;
import dev.jorel.commandapi.annotations.Subcommand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Command("rtp")
@Help("Teleports you to a safe location")
public class RtpCommand {
    
    private static final Set<Material> UNSAFE_BLOCKS = EnumSet.of(
            Material.LAVA, Material.WATER, Material.FIRE, Material.CACTUS, Material.MAGMA_BLOCK);
    private static boolean isFolia = isFolia();
    
    @Default
    public static void rtp(Player player) {
        SimpleRtp plugin = SimpleRtp.getInstance();
        int minRange = plugin.getMinRange();
        int maxAttempts = plugin.getMaxAttempts();
        FileConfiguration config = plugin.getConfigFile();
        World world = player.getWorld();
        double maxRangeValue = getMaxRange(world, config, minRange);
        
        player.sendRichMessage("<gray>Finding a safe location...</gray>");
        
        if (isFolia) {
            findSafeLocationAsync(world, maxRangeValue, 0, maxAttempts, minRange, safeLoc -> {
                if (safeLoc != null) {
                    player.getScheduler().execute(plugin, () -> {
                        player.teleportAsync(safeLoc);
                        player.sendRichMessage("<gray>Teleported <dark_aqua>successfully</dark_aqua>!<gray>");
                    }, null, 0);
                } else {
                    player.sendRichMessage("<red>Error</red><gray>: No safe location found.<gray>");
                }
            });
        } else {
            Location safeLoc = findSafeLocationSync(world, maxRangeValue, maxAttempts, minRange);
            if (safeLoc != null) {
                player.teleport(safeLoc);
                player.sendRichMessage("<gray>Teleported <dark_aqua>successfully</dark_aqua>!<gray>");
            } else {
                player.sendRichMessage("<red>Error</red><gray>: No safe location found.<gray>");
            }
        }
    }

    @Subcommand("help")
    public static void rtpHelp(Player player) {
        var plugin = SimpleRtp.getInstance();
        var minRange = plugin.getMinRange();
        var config = plugin.getConfigFile();
        var world = player.getWorld();
        var maxRangeValue = getMaxRange(world, config, minRange);

        var minRangeComp = Component.text(minRange, NamedTextColor.DARK_AQUA);
        var maxRangeComp = Component.text(maxRangeValue, NamedTextColor.DARK_AQUA);
        
        player.sendRichMessage("<dark_aqua>Usage</dark_aqua><gray>: /rtp [help]</gray>");
        player.sendRichMessage(
            "<gray>Teleports you to a safe location at <min-range>-<max-range> blocks from the spawn.</gray>",
            Placeholder.component("min-range", minRangeComp),
            Placeholder.component("max-range", maxRangeComp)
        );
        return;
    }
    
    private static double getMaxRange(World world, FileConfiguration config, int minRange) {
        WorldBorder border = world.getWorldBorder();
        double defaultMaxRange = border.getSize() / 2;
        double configMaxRange = config.getDouble("max-range", defaultMaxRange);
        return Math.max(minRange, Math.min(configMaxRange, defaultMaxRange));
    }
    
    private static void findSafeLocationAsync(
        World world, double maxRange, int attempt, int maxAttempts, int minRange, Consumer<Location> callback
    ) {
        if (attempt >= maxAttempts) {
            callback.accept(null);
            return;
        }
        
        var random = ThreadLocalRandom.current();
        int x = random.nextInt((int) maxRange * 2) - (int) maxRange;
        int z = random.nextInt((int) maxRange * 2) - (int) maxRange;
        Location loc = new Location(world, x, 0, z);
        
        if (!isWithinWorldBorder(world, loc) || !isOutsideMinRange(world, x, z, minRange)) {
            findSafeLocationAsync(world, maxRange, attempt + 1, maxAttempts, minRange, callback);
            return;
        }
        
        RegionScheduler scheduler = Bukkit.getRegionScheduler();
        scheduler.execute(SimpleRtp.getInstance(), loc, () -> {
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                int y = findSafeY(world, x, z);
                if (y != -1) {
                    callback.accept(new Location(world, x + 0.5, y + 1, z + 0.5));
                } else {
                    findSafeLocationAsync(world, maxRange, attempt + 1, maxAttempts, minRange, callback);
                }
            } else {
                world.getChunkAtAsync(chunkX, chunkZ, true, true, chunk -> {
                    if (!chunk.isLoaded()) {
                        findSafeLocationAsync(world, maxRange, attempt + 1, maxAttempts, minRange, callback);
                        return;
                    }
                    int y = findSafeY(world, x, z);
                    if (y != -1) {
                        callback.accept(new Location(world, x + 0.5, y + 1, z + 0.5));
                    } else {
                        findSafeLocationAsync(world, maxRange, attempt + 1, maxAttempts, minRange, callback);
                    }
                });
            }
        });
    }
    
    private static Location findSafeLocationSync(World world, double maximumRange, int maxAttempts, int minRange) {
        var random = ThreadLocalRandom.current();
        var maxRange = (int) maximumRange;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = random.nextInt(maxRange * 2) - maxRange;
            int z = random.nextInt(maxRange * 2) - maxRange;
            Location loc = new Location(world, x, 0, z);
            
            if (!isWithinWorldBorder(world, loc) || !isOutsideMinRange(world, x, z, minRange)) {
                continue;
            }
            
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ);
            }
            
            int y = findSafeY(world, x, z);
            if (y != -1) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
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
    
    private static boolean isWithinWorldBorder(World world, Location loc) {
        return world.getWorldBorder().isInside(loc);
    }
    
    private static boolean isOutsideMinRange(World world, int x, int z, int minRange) {
        Location spawn = world.getSpawnLocation();
        return spawn.distanceSquared(new Location(world, x, spawn.getY(), z)) >= minRange * minRange;
    }
    
    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException _) {
            return false;
        }
    }
}
