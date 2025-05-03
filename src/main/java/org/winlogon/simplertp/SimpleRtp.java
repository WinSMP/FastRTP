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
    
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadConfig();

        CommandAPI.registerCommand(RtpCommand.class);
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
