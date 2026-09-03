package com.cn.ac;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AcScanContext {
    private final AtomicBoolean inUse = new AtomicBoolean(false);

    // Dynamic scratch buffers for ring buffer
    private int[] sourceStartRing = new int[64];
    private int[] sourceEndRing = new int[64];

    public AcScanContext() {}

    public void markInUse() {
        if (!inUse.compareAndSet(false, true)) {
            throw new IllegalStateException("AcScanContext is already in use by another scan operation. Context cannot be shared concurrently or recursively.");
        }
    }

    public void releaseInUse() {
        inUse.set(false);
    }

    public void ensureRingCapacity(int capacity) {
        if (sourceStartRing.length < capacity) {
            int newCap = Math.max(capacity, sourceStartRing.length * 2);
            sourceStartRing = new int[newCap];
            sourceEndRing = new int[newCap];
        }
    }

    public int[] sourceStartRing() {
        return sourceStartRing;
    }

    public int[] sourceEndRing() {
        return sourceEndRing;
    }

    public void clear() {
        // Retain capacity, only clear inUse status if desired
        inUse.set(false);
    }

    public int retainedIntCapacity() {
        return sourceStartRing.length + sourceEndRing.length;
    }
}
