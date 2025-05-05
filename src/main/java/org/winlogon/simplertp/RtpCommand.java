package org.winlogon.simplertp;

import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Default;
import dev.jorel.commandapi.annotations.Help;
import dev.jorel.commandapi.annotations.Subcommand;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Command("rtp")
@Help("Teleports you to a safe location")
public class RtpCommand {
    private static boolean isFolia = isFolia();
    
    @Default
    public static void rtp(Player player) {
        var plugin = SimpleRtp.getInstance();
        var minRange = plugin.getMinRange();
        var maxAttempts = plugin.getMaxAttempts();
        var config = plugin.getConfigFile();
        var world = player.getWorld();
        var maxRangeValue = getMaxRange(world, config, minRange);
        
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

    // In RtpCommand.java
    private static void findSafeLocationAsync(
        World world, double maxRange, int attempt, int maxAttempts, int minRange, Consumer<Location> callback
    ) {
        Thread.startVirtualThread(() -> {
            try {
                for (; attempt < maxAttempts; attempt++) {
                    int x = ThreadLocalRandom.current().nextInt((int) maxRange * 2) - (int) maxRange;
                    int z = ThreadLocalRandom.current().nextInt((int) maxRange * 2) - (int) maxRange;
                    Location loc = new Location(world, x, 0, z);
    
                    if (!isWithinWorldBorder(world, loc) || !isOutsideMinRange(world, x, z, minRange)) {
                        continue;
                    }
    
                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
    
                    // Check if chunk is loaded on main thread
                    var isLoadedFuture = new CompletableFuture<Boolean>();
                    Bukkit.getScheduler().callSyncMethod(SimpleRtp.getInstance(), () -> world.isChunkLoaded(chunkX, chunkZ))
                        .whenComplete((isLoaded, ex) -> isLoadedFuture.complete(isLoaded));
                    boolean isLoaded = isLoadedFuture.join();
    
                    if (isLoaded) {
                        var yFuture = new CompletableFuture<Integer>();
                        Bukkit.getScheduler().callSyncMethod(SimpleRtp.getInstance(), () -> findSafeY(world, x, z))
                            .whenComplete((y, ex) -> yFuture.complete(y));
                        int y = yFuture.join();
                        if (y != -1) {
                            Location safeLoc = new Location(world, x + 0.5, y + 1, z + 0.5);
                            callback.accept(safeLoc);
                            return;
                        }
                    } else {
                        var chunkFuture = new CompletableFuture<Chunk>();
                        world.getChunkAtAsync(chunkX, chunkZ, true, chunkFuture::complete);
                        chunkFuture.join(); // Wait for chunk to load without blocking main thread
    
                        CompletableFuture<Integer> yFuture = new CompletableFuture<>();
                        Bukkit.getScheduler().callSyncMethod(SimpleRtp.getInstance(), () -> findSafeY(world, x, z))
                            .whenComplete((y, ex) -> yFuture.complete(y));
                        int y = yFuture.join();
                        if (y != -1) {
                            Location safeLoc = new Location(world, x + 0.5, y + 1, z + 0.5);
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
