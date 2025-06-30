package org.winlogon.simplertp;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LocationPool {
    // thread‑safe queue of pre‑validated safe locations
    private final List<Location> ready = Collections.synchronizedList(new ArrayList<>());
    // keeps track of which coords have been handed out already
    private final Set<Long> used = ConcurrentHashMap.newKeySet();
    private final int maxSize;

    public LocationPool(int maxSize) {
        this.maxSize = maxSize;
    }

    /** Offer a location if we haven’t used it yet. */
    public void offer(Location loc) {
        synchronized (ready) {
            if (ready.size() >= maxSize) return;

            long key = pack(loc);
            if (used.contains(key) || ready.stream().anyMatch(l -> pack(l) == key)) return;
            ready.add(loc);

            // shuffle so future poll() is random
            Collections.shuffle(ready);
        }
    }

    /** Poll the next ready location, marking it used. */
    public @Nullable Location poll() {
        synchronized (ready) {
            if (ready.isEmpty()) return null;

            Location loc = ready.remove(0);
            used.add(pack(loc));
            return loc;
        }
    }

    private long pack(Location loc) {
        // x,z fit in 32 bits each; combine into single long
        return (((long)loc.getBlockX()) << 32) | (loc.getBlockZ() & 0xFFFFFFFFL);
    }
}
