package org.winlogon.simplertp;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class RtpUtils {
    public static double getMaxRange(World world, FileConfiguration config, int minRange) {
        var border = world.getWorldBorder();
        double defaultMaxRange = border.getSize() / 2;
        double configMaxRange = config.getDouble("max-range", defaultMaxRange);
        return Math.max(minRange, Math.min(configMaxRange, defaultMaxRange));
    }
}
