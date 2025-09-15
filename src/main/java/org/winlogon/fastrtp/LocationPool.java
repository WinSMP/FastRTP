// SPDX-License-Identifier: MPL-2.0
package org.winlogon.fastrtp;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LocationPool {
    // A thread-safe list for locations ready to be used.
    private final List<Location> ready = Collections.synchronizedList(new ArrayList<>());
    // Keeps track of which coords have been handed out already
    private final BoundedUsedSet usedLocations;
    private final int maxSize;
    private boolean isPoolExhausted;

    /**
     * Constructs a new location pool.
     *
     * @param maxSize The maximum size of the pool.
     * @param usedSizeMultiplier The multiplier for the number of recently used locations to remember.
     */
    public LocationPool(int maxSize, int usedSizeMultiplier) {
        this.maxSize = maxSize;
        this.usedLocations = new BoundedUsedSet(Math.max(maxSize, maxSize * usedSizeMultiplier));
    }

    /** Offer a location if it hasn't been used recently and isn't already in the pool. */
    public boolean offer(Location loc) {
        long key = pack(loc);
        if (usedLocations.contains(key)) {
            return false;
        }

        // this modifies the ready list, so this should be synchronized
        synchronized (ready) {
            if (ready.size() >= maxSize) {
                isPoolExhausted = true;
                return false;
            }

            // check for duplicates within the ready list itself.
            if (ready.stream().anyMatch(l -> pack(l) == key)) {
                return false;
            }

            ready.add(loc);
            Collections.shuffle(ready);
            return true;
        }
    }

    /** Poll the next ready location, marking it as recently used. */
    public @Nullable Location poll() {
        Location loc;
        synchronized (ready) {
            if (ready.isEmpty()) {
                return null;
            }
            // taking from the end of an ArrayList is slightly more efficient than the start.
            loc = ready.remove(ready.size() - 1);
        }

        // the following operations are on concurrent collections and are non-blocking.
        long key = pack(loc);
        usedLocations.add(key);

        return loc;
    }

    /**
      * If the location pool has been filled up with preloaded locations, adding a new location
      * can be skipped before processing.
      *
      * @return Whether the location pool is filled.
      */
    public boolean isPoolExhausted() {
        return isPoolExhausted;
    }

    private long pack(Location loc) {
        // x,z fit in 32 bits each; combine into single long
        return (((long)loc.getBlockX()) << 32) | (loc.getBlockZ() & 0xFFFFFFFFL);
    }

    /**
     * A thread-safe, bounded set that remembers a fixed number of the most
     * recently added items. This class encapsulates all the concurrent logic for the
     * "used" set, providing a simple, safe API. This classes behaves just like a {@link Set},
     * but its type is strictly for Long.
     */
    private static class BoundedUsedSet {
        private final Queue<Long> order = new ConcurrentLinkedQueue<>();
        private final Set<Long> set = ConcurrentHashMap.newKeySet();
        private final int capacity;

        public BoundedUsedSet(int capacity) {
            this.capacity = capacity;
        }

        /**
         * Checks if the key is in the set. This is a fast, thread-safe operation.
         */
        public boolean contains(Long key) {
            return set.contains(key);
        }

        /**
         * Adds a key to the set and evicts the oldest key if the capacity is exceeded.
         * This is a thread-safe operation.
         */
        public void add(Long key) {
            if (set.add(key)) {
                order.add(key);

                // evict oldest if over capacity
                while (order.size() > capacity) {
                    var oldestKey = order.poll();
                    if (oldestKey != null) {
                        set.remove(oldestKey);
                    }
                }
            }
        }
    }
}
