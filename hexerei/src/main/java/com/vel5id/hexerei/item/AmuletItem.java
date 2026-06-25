package com.vel5id.hexerei.item;

import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Correspondence;
import com.vel5id.hexerei.soul.SealRef;
import com.mojang.serialization.DataResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A sealed amulet — a {@link Bond} carried in an {@link ItemStack}'s NBT (the {@code SealedBond} of
 * Модель §3). On 1.20.1 Forge there is no {@code DataComponentType}, so the whole record lives under one
 * NBT key, round-tripped through {@link Bond#CODEC} + {@link NbtOps} (the brew-item NBT pattern). The
 * bound spirit's {@link Correspondence} domain decides the amulet's effect and its tooltip/bar colour.
 *
 * <p>Obtained only from a sealing rite ({@code SealAmuletRite}); carried in a {@link CharmPouchItem}.
 * The wearing cost (debt accrual + seal grind) is driven server-side by {@link AmuletTickHandler}.
 */
public class AmuletItem extends Item {
    private static final String KEY = "hexerei:sealed_bond";

    public AmuletItem(Properties properties) {
        super(properties);
    }

    /** Writes a sealed bond into the stack's NBT. */
    public static void writeBond(ItemStack stack, Bond bond) {
        Tag encoded = Bond.CODEC.encodeStart(NbtOps.INSTANCE, bond)
                .result()
                .orElseThrow(() -> new IllegalStateException("failed to encode bond"));
        stack.getOrCreateTag().put(KEY, encoded);
    }

    /** The sealed bond this amulet carries, or {@code null} if it isn't sealed. */
    @Nullable
    public static Bond readBond(ItemStack stack) {
        if (!stack.hasTag()) {
            return null;
        }
        Tag raw = stack.getTag().get(KEY);
        if (raw == null) {
            return null;
        }
        DataResult<Bond> parsed = Bond.CODEC.parse(NbtOps.INSTANCE, raw);
        return parsed.result().orElse(null);
    }

    /** True if the stack carries a sealed bond. */
    public static boolean isSealed(ItemStack stack) {
        return stack.getItem() instanceof AmuletItem && readBond(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Bond bond = readBond(stack);
        if (bond == null) {
            return;
        }
        SealedAmulet.AmuletEffect fx = SealedAmulet.effectFor(bond.domain());
        if (fx != null) {
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(fx.effectId()));
            if (effect != null) {
                tooltip.add(Component.translatable(effect.getDescriptionId()).withStyle(ChatFormatting.BLUE));
            }
        }
        tooltip.add(Component.translatable("tooltip.hexerei.amulet.domain",
                Component.translatable("correspondence.hexerei." + bond.domain().key()))
                .withStyle(ChatFormatting.GRAY));
        float integrity = bond.seal() != null ? bond.seal().integrity() : 0f;
        int pct = Math.max(0, Math.round(integrity * 100f));
        tooltip.add(Component.translatable("tooltip.hexerei.amulet.integrity", pct)
                .withStyle(integrity > 0.25f ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    // ---- integrity durability-bar (the seal's hold, visible even outside a pouch) ----

    @Override
    public boolean isBarVisible(ItemStack stack) {
        Bond bond = readBond(stack);
        return bond != null && bond.seal() != null && bond.seal().integrity() < SealedAmulet.FULL_INTEGRITY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        Bond bond = readBond(stack);
        float integrity = bond != null && bond.seal() != null ? bond.seal().integrity() : 0f;
        return Math.round(13.0F * Math.max(0f, Math.min(1f, integrity)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        Bond bond = readBond(stack);
        return bond != null ? barColor(bond.domain()) : 0xFFFFFF;
    }

    private static int barColor(Correspondence domain) {
        if (domain == Correspondence.FOREST) return 0x2ECC71;     // green
        if (domain == Correspondence.DEATH) return 0x8B0000;      // dark red
        if (domain == Correspondence.THRESHOLD) return 0x9B59B6;  // witch purple
        return 0xFFFFFF;
    }
}
