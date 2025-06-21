package org.winlogon.simplertp;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jetbrains.annotations.Nullable;
import org.bukkit.Location;

public class LocationPool {
    // thread‑safe queue of pre‑validated safe locations
    private final ConcurrentLinkedQueue<Location> ready = new ConcurrentLinkedQueue<>();
    // keeps track of which coords have been handed out already
    private final Set<Long> used = ConcurrentHashMap.newKeySet();
    private final int maxSize;

    public LocationPool(int maxSize) {
        this.maxSize = maxSize;
    }

    /** Offer a location if we haven’t used it yet. */
    public void offer(Location loc) {
        if (ready.size() >= maxSize) return;
        
        long key = pack(loc);
        if (used.contains(key) || ready.stream().anyMatch(l -> pack(l) == key)) return;
        ready.offer(loc);
    }

    /** Poll the next ready location, marking it used. */
    public @Nullable Location poll() {
        Location loc = ready.poll();
        if (loc != null) used.add(pack(loc));
        return loc;
    }

    private long pack(Location loc) {
        // x,z fit in 32 bits each; combine into single long
        return (((long)loc.getBlockX()) << 32) | (loc.getBlockZ() & 0xFFFFFFFFL);
    }
}
