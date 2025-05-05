package org.winlogon.simplertp;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;

import dev.jorel.commandapi.CommandAPI;

public class SimpleRtp extends JavaPlugin {
    private RegionScheduler scheduler = Bukkit.getRegionScheduler();
    private int minRange;
    private int maxAttempts;
    private FileConfiguration config;

    private static SimpleRtp instance;
    private LocationPreloader preloader;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        preloader = new LocationPreloader(this);
        preloader.start();

        CommandAPI.registerCommand(RtpCommand.class);
        getLogger().info("SimpleRTP enabled with preloader");
    }

    @Override
    public void onDisable() {
        preloader.stop();
        getLogger().info("SimpleRTP disabled");
    }

    public LocationPreloader getPreloader() {
        return preloader;
    }
    
    private void loadConfig() {
        config = getConfig();
        minRange = config.getInt("min-range", 3000);
        maxAttempts = config.getInt("max-attempts", 50);
    }
    
    public static SimpleRtp getInstance() {
        return instance;
    }
    
    public FileConfiguration getConfigFile() {
        return config;
    }
    
    public int getMinRange() {
        return minRange;
    }
    
    public int getMaxAttempts() {
        return maxAttempts;
    }
    
    public RegionScheduler getRegionScheduler() {
        return scheduler;
    }
}
