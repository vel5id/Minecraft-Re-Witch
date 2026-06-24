package com.vel5id.hexerei.item;

/**
 * Pure charge math for a single charm, ticked once per second by {@code CharmTickHandler}.
 * Recharge near an altar always wins over drain, so an altar visit reliably tops a charm up.
 */
public final class CharmCharge {
    private CharmCharge() {}

    /** Full charge = 10 minutes of continuous active use at {@link #DRAIN} per second. */
    public static final int MAX_CHARGE = 600;
    /** Charge spent per active second. */
    public static final int DRAIN = 1;
    /** Charge gained per second within range of a powered altar — faster than drain so an altar refills several charms. */
    public static final int RECHARGE = 5;
    /** Altar power debited per unit recharged: 1 power/sec per recharging charm, so charms compete with brews/rituals. */
    public static final float RECHARGE_POWER_PER_UNIT = 0.2f;

    /** Whether the charm's condition (independent of charge) is met for this wearer right now. */
    public static boolean playerCondition(CharmDef def, float playerHealth) {
        return def.mode() != ActiveMode.LOW_HEALTH || playerHealth <= def.param();
    }

    /**
     * The charge after one second. Recharging near an altar takes priority over draining; the caller
     * debits altar power for any gain and reverts to {@code current} if the altar can't pay.
     */
    public static int nextCharge(int current, boolean wantActive, boolean altarAvailable) {
        if (altarAvailable && current < MAX_CHARGE) {
            return Math.min(MAX_CHARGE, current + RECHARGE);
        }
        if (wantActive && current > 0) {
            return Math.max(0, current - DRAIN);
        }
        return current;
    }

    /** Whether the buff actually applies this tick: it must be wanted and have charge left. */
    public static boolean isEffective(int charge, boolean wantActive) {
        return charge > 0 && wantActive;
    }
}
