package com.vel5id.hexerei.power;

public enum TaintLevel {
    NONE, LOW, MEDIUM, HIGH;

    public static TaintLevel fromValue(float v) {
        if (v >= 70f) return HIGH;
        if (v >= 40f) return MEDIUM;
        if (v >= 15f) return LOW;
        return NONE;
    }
}
