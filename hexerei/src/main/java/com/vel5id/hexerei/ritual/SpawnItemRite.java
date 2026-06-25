package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * Drops a configured item at the circle center — the generic "ritual crafting" bridge. The stack is produced
 * by a {@link Supplier} (resolved lazily, after registration is frozen), so future ritual-crafted items add
 * only a {@code RitualRecipe} line, not a new {@link Rite} class.
 */
public final class SpawnItemRite implements Rite {
    private static final int TAINT_COST = 40; // mirrors the recipe's powerCost; taint = cost/4

    private final Supplier<ItemStack> stackSupplier;

    public SpawnItemRite(Supplier<ItemStack> stackSupplier) {
        this.stackSupplier = stackSupplier;
    }

    /** A fresh copy of the supplied stack — copy so repeated casts never share or mutate one instance. */
    public static ItemStack resolve(Supplier<ItemStack> supplier) {
        return supplier.get().copy();
    }

    @Override
    public void perform(ServerLevel level, BlockPos center) {
        ItemStack out = resolve(stackSupplier);
        ItemEntity ie = new ItemEntity(level,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5, out);
        ie.setDeltaMovement(Vec3.ZERO);     // land on the circle, where a chained ritual could consume it
        ie.setDefaultPickUpDelay();
        level.addFreshEntity(ie);

        level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.2f);
        level.sendParticles(ParticleTypes.END_ROD,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                24, 0.1, 0.6, 0.1, 0.05);

        Rites.addRitualTaint(level, new ChunkPos(center), com.vel5id.hexerei.soul.Correspondence.THRESHOLD, TAINT_COST / 4f);
    }
}
