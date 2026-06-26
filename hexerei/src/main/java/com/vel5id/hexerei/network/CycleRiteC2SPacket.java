package com.vel5id.hexerei.network;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.registry.HexereiDataComponents;
import com.vel5id.hexerei.registry.HexereiItems;
import com.vel5id.hexerei.ritual.RitualRecipe;
import com.vel5id.hexerei.ritual.RitualRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** C2S: scroll on a ritual chalk to cycle which rite it draws. Writes the chalk's selected-rite component. */
public record CycleRiteC2SPacket(int delta) implements CustomPacketPayload {

    public static final Type<CycleRiteC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HexereiMod.MODID, "cycle_rite"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CycleRiteC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CycleRiteC2SPacket::delta,
                    CycleRiteC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CycleRiteC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getMainHandItem();
            if (!stack.is(HexereiItems.RITUAL_CHALK.get())) {
                stack = player.getOffhandItem();
                if (!stack.is(HexereiItems.RITUAL_CHALK.get())) return;
            }
            List<RitualRecipe> all = RitualRecipes.ALL;
            if (all.isEmpty()) return;
            String current = stack.getOrDefault(HexereiDataComponents.SELECTED_RITE.get(), "");
            int idx = 0;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id().equals(current)) { idx = i; break; }
            }
            idx = cycleIndex(idx, pkt.delta(), all.size());
            RitualRecipe next = all.get(idx);
            stack.set(HexereiDataComponents.SELECTED_RITE.get(), next.id());
            player.displayClientMessage(
                    Component.translatable(next.circleSize().circleLabelKey(),
                            next.circleSize().ringPositions(player.blockPosition()).size()),
                    true);
        });
    }

    /** Wraps {@code current + delta} into [0, size) using {@link Math#floorMod}. */
    public static int cycleIndex(int current, int delta, int size) {
        return Math.floorMod(current + delta, size);
    }
}
