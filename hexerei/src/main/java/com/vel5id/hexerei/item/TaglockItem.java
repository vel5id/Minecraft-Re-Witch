package com.vel5id.hexerei.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A taglock — a "piece" of a victim (Witchery's taglock). Right-click a {@link Player} to bind it: their UUID
 * + name are stored in the stack's NBT. A bound taglock is then dropped on a curse rite's circle to target
 * that player. Not consumed on capture (it is spent by {@code CurseRite}).
 */
public class TaglockItem extends Item {
    private static final String KEY_UUID = "hexerei:target_uuid";
    private static final String KEY_NAME = "hexerei:target_name";

    public TaglockItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) {
            return InteractionResult.PASS;
        }
        if (target instanceof Player victim) {
            bind(stack, victim);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.6f, 1.4f);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Binds this taglock to {@code victim} (server-side). */
    public static void bind(ItemStack stack, Player victim) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(KEY_UUID, victim.getUUID());
        tag.putString(KEY_NAME, victim.getName().getString());
    }

    public static boolean hasTarget(ItemStack stack) {
        return stack.hasTag() && stack.getTag().hasUUID(KEY_UUID);
    }

    @Nullable
    public static UUID getTarget(ItemStack stack) {
        return hasTarget(stack) ? stack.getTag().getUUID(KEY_UUID) : null;
    }

    @Nullable
    private static String getTargetName(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getString(KEY_NAME) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String name = getTargetName(stack);
        if (name != null && !name.isEmpty() && hasTarget(stack)) {
            tooltip.add(Component.translatable("item.hexerei.taglock.bound", name).withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            tooltip.add(Component.translatable("item.hexerei.taglock.unbound").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
