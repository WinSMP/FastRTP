package org.winlogon.simplertp;

public record RtpConfig(
    int minRange, 
    int maxAttempts, 
    int maxPoolSize, 
    int samplesPerChunk,
    int maxChunkAttempts
) {}
