// SPDX-License-Identifier: MPL-2.0
package org.winlogon.fastrtp;

import java.time.Duration;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.winlogon.fastrtp.config.PreloaderConfig;

import dev.jorel.commandapi.CommandAPI;

public class FastRtp extends JavaPlugin {
    private static FastRtp instance;

    private FileConfiguration config;
    private RtpConfig rtpConfig;

    private LocationPreloader preloader;
    private java.util.logging.Logger logger;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadConfig();
        logger = getLogger();

        preloader = new LocationPreloader(this, logger);
        preloader.start();
        logger.info("RTP preloader enabled");

        CommandAPI.registerCommand(RtpCommand.class);
    }

    @Override
    public void onDisable() {
        preloader.stop();
        logger.info("FastRTP disabled");
    }

    public LocationPreloader getPreloader() {
        return preloader;
    }
    
    private void loadConfig() {
        config = getConfig();

        int minRange         = config.getInt("min-range", 3000);
        int maxPoolSize      = config.getInt("max-pool-size", 100);
        int maxPoolMultiplier = config.getInt("max-pool-multiplier", 5);
        int samplesPerChunk  = config.getInt("samples-per-chunk", 8);

        var preloader = config.getConfigurationSection("preloader");

        int maxAttempts      = preloader.getInt("max-attempts", 50);
        int maxChunkAttempts = preloader.getInt("max-chunk-attempts", 10);
        var preloadInterval  = Duration.ofHours(preloader.getInt("preload-interval-hours", 1));
        var locationsPerHour = preloader.getInt("locations-per-hour", 5);
        double cfgMaxRange   = preloader.getDouble("max-range", -1.0);

        var preloaderConfig = new PreloaderConfig(maxAttempts, maxChunkAttempts, locationsPerHour, preloadInterval);

        rtpConfig = new RtpConfig(
            minRange,
            maxPoolSize,
            maxPoolMultiplier,
            samplesPerChunk,
            cfgMaxRange,
            preloaderConfig
        );
    }
    
    public static FastRtp getInstance() {
        return instance;
    }
    
    public FileConfiguration getConfigFile() {
        return config;
    }
    
    public RtpConfig getRtpConfig() {
        return rtpConfig;
    }
}
