// SPDX-License-Identifier: MPL-2.0
package org.winlogon.simplertp;

import org.bukkit.World;

public record RtpConfig(
    int minRange,
    int maxAttempts,
    int maxPoolSize,
    int samplesPerChunk,
    int maxChunkAttempts,
    double configMaxRange
) {
    /**
     * Compute the actual maximum range for RTP in this world:
     *   - take the world-border radius (border.getSize()/2)
     *   - if the user specified a max-range > 0, honor it (but never exceed the border)
     *   - always enforce at least minRange
     */
    public double getMaxRange(World world) {
        double borderRadius = world.getWorldBorder().getSize() / 2.0;

        // if the config value is <= 0, act as "unspecified" -> use border / 2
        double capped = configMaxRange > 0
            ? Math.min(configMaxRange, borderRadius)
            : borderRadius;
        return Math.max(minRange, capped);
    }
}
