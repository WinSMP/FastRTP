package org.winlogon.simplertp;

import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Default;
import dev.jorel.commandapi.annotations.Help;
import dev.jorel.commandapi.annotations.Subcommand;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.Set;
import java.util.EnumSet;
import java.util.Optional;

@Command("rtp")
@Help("Teleports you to a safe location")
public class RtpCommand {
    private static boolean isFolia = isFolia();
    private static final Set<Material> UNSAFE_BLOCKS = EnumSet.of(
        Material.LAVA, Material.WATER, Material.FIRE, Material.CACTUS, Material.MAGMA_BLOCK
    );

    private static SimpleRtp plugin = SimpleRtp.getInstance();
    private static RtpConfig rtpConfig = plugin.getRtpConfig();
    private static ExecutorService executor = plugin.getVirtualExecutor();
    
    @Default
    public static void rtp(Player player) {
        var minRange = rtpConfig.minRange();
        var maxAttempts = rtpConfig.maxAttempts();

        var config = plugin.getConfigFile();
        var world = player.getWorld();

        var maxRangeValue = RtpUtils.getMaxRange(world, config, minRange);
        
        player.sendRichMessage("<gray>Finding a safe location...</gray>");
        
        // FIXME: duplicated code - how do i deduplicate this without fucking up the methods' API?
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
            var safeLoc = findSafeLocationSync(world, maxRangeValue, maxAttempts, minRange);
            if (safeLoc.isPresent()) {
                player.teleport(safeLoc.get());
                player.sendRichMessage("<gray>Teleported <dark_aqua>successfully</dark_aqua>!<gray>");
            } else {
                player.sendRichMessage("<red>Error</red><gray>: No safe location found.<gray>");
            }
        }
    }

    @Subcommand("help")
    public static void rtpHelp(Player player) {
        var plugin = SimpleRtp.getInstance();
        var rtpConfig = plugin.getRtpConfig();

        var minRange = rtpConfig.minRange();
        var config = plugin.getConfigFile();
        var world = player.getWorld();
        var maxRangeValue = RtpUtils.getMaxRange(world, config, minRange);

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

    private static void findSafeLocationAsync(
        World world, double maxRange, int attempt, int maxAttempts, int minRange, Consumer<Location> callback
    ) {
        executor.submit(() -> {
            try {
                int totalSamples = 0;
                var plugin = SimpleRtp.getInstance();
                int samplesPerChunk = plugin.getRtpConfig().samplesPerChunk();
                
                while (totalSamples < maxAttempts) {
                    // Generate random chunk coordinates
                    int chunkX = ThreadLocalRandom.current().nextInt((int) maxRange * 2) - (int) maxRange;
                    int chunkZ = ThreadLocalRandom.current().nextInt((int) maxRange * 2) - (int) maxRange;
                    
                    // Load chunk asynchronously
                    var chunkFuture = new CompletableFuture<Chunk>();
                    world.getChunkAtAsync(chunkX, chunkZ, true, chunkFuture::complete);
                    Chunk chunk = chunkFuture.join();
                    
                    // Create chunk snapshot
                    var snap = chunk.getChunkSnapshot();
                    
                    // Sample multiple locations in the same chunk
                    for (int i = 0; i < samplesPerChunk && totalSamples < maxAttempts; i++, totalSamples++) {
                        int localX = ThreadLocalRandom.current().nextInt(16);
                        int localZ = ThreadLocalRandom.current().nextInt(16);
                        int x = (chunkX << 4) + localX;
                        int z = (chunkZ << 4) + localZ;
                        Location loc = new Location(world, x, 0, z);

                        if (!isWithinWorldBorder(world, loc) || !isOutsideMinRange(world, x, z, minRange)) {
                            continue;
                        }

                        // Use snapshot for fast height lookup
                        int highestY = snap.getHighestBlockYAt(localX, localZ);
                        if (highestY < world.getMinHeight() || highestY >= world.getMaxHeight() - 2) continue;

                        var above1 = snap.getBlockType(localX, highestY + 1, localZ);
                        var above2 = snap.getBlockType(localX, highestY + 2, localZ);
                        var below = snap.getBlockType(localX, highestY - 1, localZ);

                        if (above1 == Material.AIR && 
                            above2 == Material.AIR && 
                            !UNSAFE_BLOCKS.contains(below)) {
                            
                            Location safeLoc = new Location(world, x + 0.5, highestY + 1, z + 0.5);
                            callback.accept(safeLoc);
                            return;
                        }
                    }
                }
                callback.accept(null);
            } catch (Exception e) {
                e.printStackTrace();
                callback.accept(null);
            }
        });
    }
    
    private static Optional<Location> findSafeLocationSync(World world, double maximumRange, int maxAttempts, int minRange) {
        var plugin = SimpleRtp.getInstance();
        int samplesPerChunk = plugin.getRtpConfig().samplesPerChunk();
        int totalSamples = 0;
        int maxRangeInt = (int) maximumRange;
        
        while (totalSamples < maxAttempts) {
            int chunkX = ThreadLocalRandom.current().nextInt(maxRangeInt * 2) - maxRangeInt;
            int chunkZ = ThreadLocalRandom.current().nextInt(maxRangeInt * 2) - maxRangeInt;
            
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ);
            }
            
            var chunk = world.getChunkAt(chunkX, chunkZ);
            var snap = chunk.getChunkSnapshot();
            
            for (int i = 0; i < samplesPerChunk && totalSamples < maxAttempts; i++, totalSamples++) {
                int localX = ThreadLocalRandom.current().nextInt(16);
                int localZ = ThreadLocalRandom.current().nextInt(16);
                int x = (chunkX << 4) + localX;
                int z = (chunkZ << 4) + localZ;
                Location loc = new Location(world, x, 0, z);

                if (!isWithinWorldBorder(world, loc) || !isOutsideMinRange(world, x, z, minRange)) {
                    continue;
                }

                int highestY = snap.getHighestBlockYAt(localX, localZ);
                if (highestY < world.getMinHeight() || highestY >= world.getMaxHeight() - 2) continue;

                Material above1 = world.getBlockAt(x, highestY + 1, z).getType();
                Material above2 = world.getBlockAt(x, highestY + 2, z).getType();
                Material below = world.getBlockAt(x, highestY - 1, z).getType();

                if (above1 == Material.AIR && 
                    above2 == Material.AIR && 
                    !UNSAFE_BLOCKS.contains(below)) {
                    
                    return Optional.of(new Location(world, x + 0.5, highestY + 1, z + 0.5));
                }
            }
        }
        return Optional.empty();
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
