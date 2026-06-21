package com.vel5id.hexerei.power;

/** A single counted power contribution accumulated by an altar as it tallies surrounding blocks. */
public final class PowerSource {
    private final int factor;
    private final int limit;
    private int count;

    public PowerSource(int factor, int limit) {
        this.factor = factor;
        this.limit = limit;
    }

    public void increment() {
        count++;
    }

    public int count() {
        return count;
    }

    /** power = min(count, limit) * factor */
    public int getPower() {
        return Math.min(count, limit) * factor;
    }
}
