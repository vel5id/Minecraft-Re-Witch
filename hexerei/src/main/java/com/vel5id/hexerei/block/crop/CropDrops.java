package com.vel5id.hexerei.block.crop;

import net.minecraft.util.RandomSource;

/** Hexerei herb-crop harvest roll selection (mandrake entity-spawn deferred). */
public final class CropDrops {
    private CropDrops() {}

    public record Roll(int seeds, int produce, boolean icyNeedle) {}

    public static Roll roll(RandomSource r, boolean mature, int fortune, boolean mindrake, boolean snowbell) {
        if (!mature) {
            return new Roll(1, 0, false);                       // immature: 1 seed
        }
        if (mindrake) {
            return new Roll(1, r.nextInt(4) == 0 ? 1 : 0, false); // 1 seed + 25% produce
        }
        int seeds = 0;
        for (int n = 0; n < 3 + fortune; n++) {
            if (r.nextInt(15) <= 7) {                           // ~8/15 each
                seeds++;
            }
        }
        boolean needle = snowbell && r.nextDouble() <= 0.2;     // snowbell bonus 20%
        return new Roll(seeds, 1, needle);                      // + 1 produce
    }
}
