package org.winlogon.fastrtp.config;

import java.time.Duration;

public record PreloaderConfig(
    int maxAttempts,
    int maxChunkAttempts,
    int locationsPerHour,
    Duration preloadInterval
) {}
