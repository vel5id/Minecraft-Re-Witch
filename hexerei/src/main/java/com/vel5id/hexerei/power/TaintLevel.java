package com.vel5id.hexerei.power;

public enum TaintLevel {
    NONE, LOW, MEDIUM, HIGH;

    public static TaintLevel fromValue(float v) {
        if (v >= 70f) return HIGH;
        if (v >= 40f) return MEDIUM;
        if (v >= 15f) return LOW;
        return NONE;
    }

    /**
     * Number of wisp particles emitted per emission tick for this taint level.
     * NONE=0, LOW=1, MEDIUM=2, HIGH=3 (plus 1 ash particle handled separately).
     */
    public int wispCount() {
        return switch (this) {
            case NONE   -> 0;
            case LOW    -> 1;
            case MEDIUM -> 2;
            case HIGH   -> 3;
        };
    }

    /**
     * Number of ash particles emitted per emission tick for this taint level.
     * Only HIGH produces ash.
     */
    public int ashCount() {
        return this == HIGH ? 1 : 0;
    }
}
