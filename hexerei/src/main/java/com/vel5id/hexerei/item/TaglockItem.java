package com.vel5id.hexerei.item;

import com.vel5id.hexerei.registry.HexereiDataComponents;
import com.vel5id.hexerei.registry.HexereiDataComponents.TaglockTarget;
import net.minecraft.ChatFormatting;
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

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A taglock — a "piece" of a victim (Witchery's taglock). Right-click a {@link Player} to bind it: their UUID
 * + name are stored in the stack's {@link HexereiDataComponents#TAGLOCK_TARGET} component. A bound taglock is
 * then dropped on a curse rite's circle to target that player. Not consumed on capture (it is spent by
 * {@code CurseRite}).
 */
public class TaglockItem extends Item {

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
        stack.set(HexereiDataComponents.TAGLOCK_TARGET.get(),
                new TaglockTarget(victim.getUUID(), victim.getName().getString()));
    }

    public static boolean hasTarget(ItemStack stack) {
        return stack.has(HexereiDataComponents.TAGLOCK_TARGET.get());
    }

    @Nullable
    public static UUID getTarget(ItemStack stack) {
        TaglockTarget t = stack.get(HexereiDataComponents.TAGLOCK_TARGET.get());
        return t != null ? t.id() : null;
    }

    @Nullable
    private static String getTargetName(ItemStack stack) {
        TaglockTarget t = stack.get(HexereiDataComponents.TAGLOCK_TARGET.get());
        return t != null ? t.name() : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String name = getTargetName(stack);
        if (name != null && !name.isEmpty() && hasTarget(stack)) {
            tooltip.add(Component.translatable("item.hexerei.taglock.bound", name).withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            tooltip.add(Component.translatable("item.hexerei.taglock.unbound").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
