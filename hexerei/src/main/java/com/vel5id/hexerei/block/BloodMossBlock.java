package com.vel5id.hexerei.block;

import net.minecraft.world.level.block.CarpetBlock;

/**
 * Blood Moss: a flat, carpet-style ground decoration (1px tall, needs support below) that reads as
 * a red moss cover. v1 is non-spreading (deterministic) — vanilla {@link CarpetBlock} survival only.
 * Drops itself via loot JSON; contributes 4/20 altar power via AltarBlockEntity.resolveDynamic.
 */
public class BloodMossBlock extends CarpetBlock {
    public BloodMossBlock(Properties props) {
        super(props);
    }
}
