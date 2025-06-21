package org.winlogon.simplertp;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;

import dev.jorel.commandapi.CommandAPI;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class SimpleRtp extends JavaPlugin {
    private RegionScheduler scheduler = Bukkit.getRegionScheduler();
    private static SimpleRtp instance;

    private FileConfiguration config;
    private RtpConfig rtpConfig;

    private LocationPreloader preloader;
    ExecutorService executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("rtp-", 0).factory()
    );

    private java.util.logging.Logger logger;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadConfig();
        logger = getLogger();

        preloader = new LocationPreloader(this, executor, logger);
        preloader.start();
        logger.info("RTP preloader enabled");

        CommandAPI.registerCommand(RtpCommand.class);
    }

    @Override
    public void onDisable() {
        preloader.stop();
        executor.shutdown();
        logger.info("SimpleRTP disabled");
    }

    public LocationPreloader getPreloader() {
        return preloader;
    }
    
    private void loadConfig() {
        config = getConfig();
        int minRange         = config.getInt("min-range", 3000);
        int maxAttempts      = config.getInt("max-attempts", 50);
        int maxPoolSize      = config.getInt("max-pool-size", 100);
        int samplesPerChunk  = config.getInt("samples-per-chunk", 8);
        int maxChunkAttempts = config.getInt("max-chunk-attempts", 10);
        rtpConfig = new RtpConfig(minRange, maxAttempts, maxPoolSize, samplesPerChunk, maxChunkAttempts);
    }
    
    public static SimpleRtp getInstance() {
        return instance;
    }
    
    public FileConfiguration getConfigFile() {
        return config;
    }
    
    public RtpConfig getRtpConfig() {
        return rtpConfig;
    }
    
    public RegionScheduler getRegionScheduler() {
        return scheduler;
    }

    public ExecutorService getVirtualExecutor() {
        return executor;
    }
}
