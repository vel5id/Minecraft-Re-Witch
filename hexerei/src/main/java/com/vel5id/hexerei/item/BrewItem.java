package com.vel5id.hexerei.item;

import com.vel5id.hexerei.brewing.Brew;
import com.vel5id.hexerei.brewing.BrewColor;
import com.vel5id.hexerei.brewing.BrewEffect;
import com.vel5id.hexerei.brewing.Brews;
import com.vel5id.hexerei.registry.HexereiItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** A drinkable brew. Its identity (a {@link Brew} id) lives in NBT; on drink it applies the brew's effects. */
public class BrewItem extends Item {
    private static final String BREW_ID = "BrewId";

    public BrewItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Brew brew) {
        ItemStack stack = new ItemStack(HexereiItems.BREW.get());
        stack.getOrCreateTag().putString(BREW_ID, brew.id());
        return stack;
    }

    @Nullable
    public static Brew brewOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? Brews.byId(tag.getString(BREW_ID)) : null;
    }

    /** Liquid tint for the colored-potion overlay (tintindex 0). */
    public static int color(ItemStack stack) {
        Brew brew = brewOf(stack);
        return brew != null ? brew.color() : BrewColor.WATER;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Brew brew = brewOf(stack);
        Player player = entity instanceof Player p ? p : null;
        if (brew != null && !level.isClientSide) {
            for (BrewEffect e : brew.effects()) {
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(e.effectId()));
                if (effect != null) {
                    entity.addEffect(new MobEffectInstance(effect, e.durationTicks(), e.amplifier()));
                }
            }
        }
        // In creative the brew isn't consumed, so don't hand out a glass bottle (would dupe bottles).
        if (player != null && player.getAbilities().instabuild) {
            return stack;
        }
        stack.shrink(1);
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (stack.isEmpty()) {
            return bottle;
        }
        if (player != null && !player.getInventory().add(bottle)) {
            player.drop(bottle, false);
        }
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        Brew brew = brewOf(stack);
        return brew != null ? Component.translatable(brew.nameKey()) : super.getName(stack);
    }
}
