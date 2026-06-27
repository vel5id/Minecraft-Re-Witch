package com.vel5id.hexerei.soul;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** The hexerei:dream level key + the barren central platform + the wake/return teleport. */
public final class DreamWorld {
    private DreamWorld() {}

    public static final ResourceKey<Level> DREAM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(HexereiMod.MODID, "dream"));
    public static final BlockPos ANCHOR = new BlockPos(0, 64, 0);   // spawn stands on the y=63 platform [UNVERIFIED]
    public static final float DEATH_WAKE_HEALTH = 4f;               // 2 hearts on a death-wake [UNVERIFIED]

    /** Ensure a 5x5 barren stone platform exists under the anchor (idempotent). */
    public static void preparePlatform(ServerLevel dream) {
        int y = ANCHOR.getY() - 1;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos p = new BlockPos(ANCHOR.getX() + x, y, ANCHOR.getZ() + z);
                if (!dream.getBlockState(p).is(Blocks.STONE)) {
                    dream.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
    }

    /** Wake the player: teleport to the saved return target (or overworld spawn) and clear the flag. */
    public static void wake(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        DreamState st = player.getData(HexereiAttachments.DREAM_STATE);
        ServerLevel over = server.overworld();
        DreamReturn target = DreamReturn.resolveTarget(
                st.returnDim(), st.returnPos(), over.dimension(), over.getSharedSpawnPos());
        ServerLevel dest = server.getLevel(target.dim());
        if (dest == null) dest = over;
        BlockPos p = target.pos();
        player.teleportTo(dest, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.getYRot(), player.getXRot());
        st.clear();
    }
}
