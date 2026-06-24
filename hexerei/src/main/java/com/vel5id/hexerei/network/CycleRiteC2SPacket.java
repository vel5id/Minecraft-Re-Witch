package com.vel5id.hexerei.network;

import com.vel5id.hexerei.registry.HexereiItems;
import com.vel5id.hexerei.ritual.RitualRecipe;
import com.vel5id.hexerei.ritual.RitualRecipes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record CycleRiteC2SPacket(int delta) {
    public static void encode(CycleRiteC2SPacket p, FriendlyByteBuf buf) { buf.writeInt(p.delta()); }
    public static CycleRiteC2SPacket decode(FriendlyByteBuf buf) { return new CycleRiteC2SPacket(buf.readInt()); }

    public static void handle(CycleRiteC2SPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ItemStack stack = player.getMainHandItem();
            if (!stack.is(HexereiItems.RITUAL_CHALK.get())) {
                stack = player.getOffhandItem();
                if (!stack.is(HexereiItems.RITUAL_CHALK.get())) return;
            }
            List<RitualRecipe> all = RitualRecipes.ALL;
            if (all.isEmpty()) return;
            String current = stack.hasTag() ? stack.getOrCreateTag().getString("hexerei:rite") : "";
            int idx = 0;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id().equals(current)) { idx = i; break; }
            }
            idx = cycleIndex(idx, pkt.delta(), all.size());
            RitualRecipe next = all.get(idx);
            stack.getOrCreateTag().putString("hexerei:rite", next.id());
            player.displayClientMessage(
                    Component.translatable(next.circleSize().circleLabelKey(),
                            next.circleSize().ringPositions(player.blockPosition()).size()),
                    true);
        });
        ctx.get().setPacketHandled(true);
    }

    /** Wraps {@code current + delta} into [0, size) using {@link Math#floorMod}. */
    public static int cycleIndex(int current, int delta, int size) {
        return Math.floorMod(current + delta, size);
    }
}
