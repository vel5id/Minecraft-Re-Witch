package com.vel5id.hexerei.soul;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The altar drains the life of the land around it (Статья III): it consumes the NEAREST living block
 * in reach — grass to dirt, leaves/flowers/grass/witch-crops to nothing — freeing essence and leaving
 * a spreading blight that {@code disturbance[domain]} records. Power is the destruction it causes;
 * when the ground is bared, the well runs dry until life returns or the witch widens her reach.
 *
 * <p>Block selection is deterministic (nearest first → the scar grows outward from the altar), not a
 * dice roll (Art. II.6). Server-only. The take-math is {@link EssenceSource} (unit-tested).
 */
public final class AltarDrain {
    private AltarDrain() {}

    private enum Mode { TO_AIR, TO_DIRT }

    private record Consumable(Correspondence domain, float magnitude, float defilement, Mode mode) {}

    /**
     * Consume the nearest living block within {@code radius} of {@code center}, write its disturbance,
     * and return the take {@link Act} (so the caller can credit essence). {@code null} if nothing is
     * left to drain.
     */
    public static Act drainNearest(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        Consumable bestC = null;
        for (BlockPos p : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            Consumable c = classify(level.getBlockState(p));
            if (c == null) continue;
            double d = center.distSqr(p);
            if (d < bestDist) {
                bestDist = d;
                best = p.immutable();
                bestC = c;
            }
        }
        if (best == null) return null;

        level.setBlock(best, (bestC.mode == Mode.TO_DIRT ? Blocks.DIRT : Blocks.AIR).defaultBlockState(), 3);
        Act act = EssenceSource.breakRelease(bestC.domain, bestC.magnitude, bestC.defilement);

        LevelChunk chunk = level.getChunkAt(best);
        chunk.getCapability(HexereiCapabilities.CHUNK_SOUL).ifPresent(soul -> soul.apply(act));
        return act;
    }

    /** What a block is worth to the altar's hunger, or {@code null} if it is not living matter. */
    private static Consumable classify(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.GRASS_BLOCK) {
            return new Consumable(Correspondence.FOREST, 0.10f, 0f, Mode.TO_DIRT); // strip the turf
        }
        if (state.is(BlockTags.LEAVES)) {
            return new Consumable(Correspondence.FOREST, 0.15f, 0f, Mode.TO_AIR);
        }
        // witch crops / mushrooms / moss carry their own domain (ReleaseBlocks)
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b);
        ReleaseBlocks.Release rel = id == null ? null : ReleaseBlocks.get(id);
        if (rel != null) {
            return new Consumable(rel.domain(), rel.magnitude(), rel.defilement(), Mode.TO_AIR);
        }
        if (b instanceof BushBlock) {                                  // vanilla grass, ferns, flowers, saplings
            return new Consumable(Correspondence.FOREST, 0.10f, 0f, Mode.TO_AIR);
        }
        return null;
    }
}
