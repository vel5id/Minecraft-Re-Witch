package com.vel5id.hexerei.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An altar artefact. The item's identity IS its {@link ArtefactDef} (one item instance per artefact);
 * the def carries no NBT. Placed in the altar's single slot, it scales the
 * taint/effect of rituals that altar funds.
 */
public class ArtefactItem extends Item {
    private final ArtefactDef def;

    public ArtefactItem(Properties properties, ArtefactDef def) {
        super(properties.stacksTo(1));
        this.def = def;
    }

    public ArtefactDef def() {
        return def;
    }

    /** The {@link ArtefactDef} of this stack, or {@code null} if it isn't an artefact. */
    @Nullable
    public static ArtefactDef defOf(ItemStack stack) {
        return stack.getItem() instanceof ArtefactItem a ? a.def : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Archetype label: an amplifier raises taint (taintMul > 1), a purifier dampens it.
        String archetypeKey = def.taintMul() > 1.0f
                ? "tooltip.hexerei.artefact.amplifier"
                : "tooltip.hexerei.artefact.purifier";
        tooltip.add(Component.translatable(archetypeKey)
                .withStyle(def.taintMul() > 1.0f ? ChatFormatting.RED : ChatFormatting.AQUA));

        tooltip.add(Component.translatable("tooltip.hexerei.artefact.taint_mul", format(def.taintMul()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hexerei.artefact.effect_mul", format(def.effectMul()))
                .withStyle(ChatFormatting.GRAY));
    }

    /** Formats a multiplier as e.g. {@code "0.5"} / {@code "2"} — trims a trailing {@code ".0"}. */
    private static String format(float mul) {
        return mul == Math.rint(mul) ? Integer.toString((int) mul) : Float.toString(mul);
    }
}
