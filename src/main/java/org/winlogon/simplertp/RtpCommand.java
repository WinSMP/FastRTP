// SPDX-License-Identifier: MPL-2.0
package org.winlogon.simplertp;

import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Default;
import dev.jorel.commandapi.annotations.Help;
import dev.jorel.commandapi.annotations.Permission;
import dev.jorel.commandapi.annotations.Subcommand;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.winlogon.asynccraftr.AsyncCraftr;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Logger;

@Command("rtp")
@Permission("simplertp.rtp")
@Help("Teleports you to a safe location")
public class RtpCommand {
    private static boolean isFolia = isFolia();
    private static final Set<Material> UNSAFE_BLOCKS = EnumSet.of(
        Material.LAVA, Material.WATER, Material.FIRE, Material.CACTUS, Material.MAGMA_BLOCK
    );

    private static SimpleRtp plugin = SimpleRtp.getInstance();
    private static RtpConfig config = plugin.getRtpConfig();
    private static Logger logger = plugin.getLogger();
    
    @Default
    public static void rtp(Player player) {
        var plugin = SimpleRtp.getInstance();
        var world  = player.getWorld();
        var minRange = plugin.getRtpConfig().minRange();
        var maxAttempts = plugin.getRtpConfig().maxAttempts();
        var maxRangeValue = config.getMaxRange(world);

        player.sendRichMessage("<gray>Finding a safe location...</gray>");

        var preloadedLocation = plugin.getPreloader().nextPreloadedLocation();

        CompletableFuture<Location> locFuture = preloadedLocation
            .map(CompletableFuture::completedFuture)
            .orElseGet(() -> AsyncCraftr.runAsync(plugin, () -> findSafeLocation(world, maxRangeValue, maxAttempts, minRange) ));
        locFuture.thenAccept(safeLoc -> {
            if (safeLoc != null) {
                AsyncCraftr.runSyncForEntity(plugin, player, () -> {
                    player.teleportAsync(safeLoc);
                    player.sendRichMessage("<gray>Teleported <dark_aqua>successfully</dark_aqua>!");
                    return null;
                });
            } else {
                player.sendRichMessage("<red>Error</red><gray>: No safe location found.</gray>");
            }
        });
    }

    @Subcommand("help")
    public static void rtpHelp(Player player) {
        var rtpConfig = SimpleRtp.getInstance().getRtpConfig();

        var minRange = rtpConfig.minRange();
        var world = player.getWorld();
        var maxRangeValue = rtpConfig.getMaxRange(world);

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

    private static Location findSafeLocation(
        World world, double maximumRange, int maxAttempts, int minRange
    ) {
        var plugin = SimpleRtp.getInstance();
        int samplesPerChunk = plugin.getRtpConfig().samplesPerChunk();
        int totalSamples = 0;
        int maxRangeInt = (int) maximumRange;

        int chunkRange = maxRangeInt / 16;
        int bound = chunkRange * 2 + 1;

        
        while (totalSamples < maxAttempts) {
            int chunkX = ThreadLocalRandom.current().nextInt(bound) - maxRangeInt;
            int chunkZ = ThreadLocalRandom.current().nextInt(bound) - maxRangeInt;
            
            try {
                var chunkFuture = world.getChunkAtAsync(chunkX, chunkZ, true);
                
                var snap = chunkFuture.get().getChunkSnapshot();
                
                for (int i = 0; i < samplesPerChunk && totalSamples < maxAttempts; i++, totalSamples++) {
                    var localX = ThreadLocalRandom.current().nextInt(16);
                    var localZ = ThreadLocalRandom.current().nextInt(16);
                    var x = (chunkX << 4) + localX;
                    var z = (chunkZ << 4) + localZ;
                    var loc = new Location(world, x, 0, z);

                    if (!isWithinWorldBorder(world, loc) || !isOutsideMinRange(world, x, z, minRange)) {
                        continue;
                    }

                    int highestY = snap.getHighestBlockYAt(localX, localZ);
                    if (highestY < world.getMinHeight() || highestY >= world.getMaxHeight() - 2) continue;

                    Material above1 = snap.getBlockType(localX, highestY + 1, localZ);
                    Material above2 = snap.getBlockType(localX, highestY + 2, localZ);
                    Material below = snap.getBlockType(localX, highestY - 1, localZ);

                    if (above1 == Material.AIR && 
                        above2 == Material.AIR && 
                        !UNSAFE_BLOCKS.contains(below)) {
                        
                        return new Location(world, x + 0.5, highestY + 1, z + 0.5);
                    }
                }
            } catch (Exception e) {
                logger.info("Error finding safe location" + e.getMessage());
            }
        }
        return null;
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
