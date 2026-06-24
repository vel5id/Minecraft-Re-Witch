package com.vel5id.hexerei.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A combat charm. The item's identity IS its {@link CharmDef} (one item class instance per charm);
 * only the mutable charge lives in NBT. Carried inside a {@link CharmPouchItem}, it grants a passive buff.
 */
public class CharmItem extends Item {
    private static final String KEY_CHARGE = "hexerei:Charge";

    private final CharmDef def;

    public CharmItem(Properties properties, CharmDef def) {
        super(properties.stacksTo(1));
        this.def = def;
    }

    public CharmDef def() {
        return def;
    }

    /** The {@link CharmDef} of this stack, or {@code null} if it isn't a charm. */
    @Nullable
    public static CharmDef defOf(ItemStack stack) {
        return stack.getItem() instanceof CharmItem c ? c.def : null;
    }

    /** The vanilla effect a charm def grants, or {@code null} if its id doesn't resolve. */
    @Nullable
    public static MobEffect resolveEffect(CharmDef def) {
        return BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(def.effectId()));
    }

    /** The vanilla effect this charm grants, or {@code null} if the id doesn't resolve. */
    @Nullable
    public MobEffect effect() {
        return resolveEffect(def);
    }

    /** Stored charge. A fresh charm (no NBT) reads as full, so creative-tab charms are usable immediately. */
    public static int getCharge(ItemStack stack) {
        CompoundTag tag = stack.hasTag() ? stack.getTag() : null;
        return tag != null && tag.contains(KEY_CHARGE) ? tag.getInt(KEY_CHARGE) : CharmCharge.MAX_CHARGE;
    }

    public static void setCharge(ItemStack stack, int charge) {
        stack.getOrCreateTag().putInt(KEY_CHARGE, Mth.clamp(charge, 0, CharmCharge.MAX_CHARGE));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MobEffect e = effect();
        if (e != null) {
            tooltip.add(Component.translatable(e.getDescriptionId()).withStyle(ChatFormatting.BLUE));
        }
        if (getCharge(stack) <= 0) {
            tooltip.add(Component.translatable("tooltip.hexerei.charm.dormant").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
