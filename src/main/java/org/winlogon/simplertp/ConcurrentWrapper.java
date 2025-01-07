package org.winlogon.simplertp;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A thread-safe wrapper for a value of type T.
 
 * @param <T> The type of value to wrap
 */
public class ConcurrentWrapper<T> {
    private final AtomicReference<T> reference;

    /**
     * Creates a new ConcurrentWrapper with the given initial value.

     * @param initialValue The initial value
     */
    public ConcurrentWrapper(T initialValue) {
        this.reference = new AtomicReference<>(initialValue);
    }

    /**
     * Returns the wrapped value.

     * @return The wrapped value
     */
    public T get() {
        return reference.get();
    }

    /**
     * Sets the wrapped value.

     * @param value The value to set
     */
    public void set(T value) {
        reference.set(value);
    }

    /**
     * Atomically updates the wrapped value if it is equal to the expected value.

     * @param expected The expected value
     * @param update The value to update to
     * @return True if the update was successful, false otherwise
     */
    public boolean compareAndSet(T expected, T update) {
        return reference.compareAndSet(expected, update);
    }
}
