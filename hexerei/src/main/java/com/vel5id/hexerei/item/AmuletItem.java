package com.vel5id.hexerei.item;

import com.vel5id.hexerei.registry.HexereiDataComponents;
import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Correspondence;
import com.vel5id.hexerei.soul.SealRef;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A sealed amulet — a {@link Bond} carried in an {@link ItemStack} (the {@code SealedBond} of Модель §3).
 * On 1.21.1 the record lives in a typed {@code DataComponentType<Bond>}
 * ({@link HexereiDataComponents#SEALED_BOND}), codec-backed by {@link Bond#CODEC}. The bound spirit's
 * {@link Correspondence} domain decides the amulet's effect and its tooltip/bar colour.
 *
 * <p>Obtained only from a sealing rite ({@code SealAmuletRite}); carried in a {@link CharmPouchItem}.
 * The wearing cost (debt accrual + seal grind) is driven server-side by {@link AmuletTickHandler}.
 */
public class AmuletItem extends Item {

    public AmuletItem(Properties properties) {
        super(properties);
    }

    /** Writes a sealed bond into the stack's component. */
    public static void writeBond(ItemStack stack, Bond bond) {
        stack.set(HexereiDataComponents.SEALED_BOND.get(), bond);
    }

    /** The sealed bond this amulet carries, or {@code null} if it isn't sealed. */
    @Nullable
    public static Bond readBond(ItemStack stack) {
        return stack.get(HexereiDataComponents.SEALED_BOND.get());
    }

    /** True if the stack carries a sealed bond. */
    public static boolean isSealed(ItemStack stack) {
        return stack.getItem() instanceof AmuletItem && readBond(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Bond bond = readBond(stack);
        if (bond == null) {
            return;
        }
        SealedAmulet.AmuletEffect fx = SealedAmulet.effectFor(bond.domain());
        if (fx != null) {
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(fx.effectId()));
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
