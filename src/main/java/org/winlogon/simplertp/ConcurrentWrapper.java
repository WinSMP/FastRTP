package org.winlogon.simplertp;

import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentWrapper<T> {
    private final AtomicReference<T> reference;

    public ConcurrentWrapper(T initialValue) {
        this.reference = new AtomicReference<>(initialValue);
    }

    public T get() {
        return reference.get();
    }

    public void set(T value) {
        reference.set(value);
    }

    public boolean compareAndSet(T expected, T update) {
        return reference.compareAndSet(expected, update);
    }
}
