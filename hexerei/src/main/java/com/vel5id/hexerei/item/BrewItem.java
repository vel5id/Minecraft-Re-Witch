package com.vel5id.hexerei.item;

import com.vel5id.hexerei.brewing.Brew;
import com.vel5id.hexerei.brewing.BrewColor;
import com.vel5id.hexerei.brewing.BrewEffect;
import com.vel5id.hexerei.brewing.Brews;
import com.vel5id.hexerei.registry.HexereiDataComponents;
import com.vel5id.hexerei.registry.HexereiItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A drinkable brew. Its identity (a {@link Brew} id) lives in the
 * {@link HexereiDataComponents#BREW_ID} component; on drink it applies the brew's effects.
 */
public class BrewItem extends Item {

    public BrewItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Brew brew) {
        ItemStack stack = new ItemStack(HexereiItems.BREW.get());
        stack.set(HexereiDataComponents.BREW_ID.get(), brew.id());
        return stack;
    }

    @Nullable
    public static Brew brewOf(ItemStack stack) {
        String id = stack.get(HexereiDataComponents.BREW_ID.get());
        return id != null ? Brews.byId(id) : null;
    }

    /** Liquid tint — no longer wired to a render tint; kept for cauldron blend / future GUI use. // FUTURE */
    public static int color(ItemStack stack) {
        Brew brew = brewOf(stack);
        return brew != null ? brew.color() : BrewColor.WATER;
    }

    /** 0 when no/unknown brew component, else the 1..N catalog index — drives the per-brew item-model override. */
    public static int modelIndex(ItemStack stack) {
        String id = stack.get(HexereiDataComponents.BREW_ID.get());
        return id == null ? 0 : Brews.indexOf(id);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
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
                Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(e.effectId())).orElse(null);
                if (effect != null) {
                    entity.addEffect(new MobEffectInstance(effect, e.durationTicks(), e.amplifier()));
                }
            }
            if (brew.id().equals(Brews.DREAMING_DRAUGHT.id())
                    && entity instanceof net.minecraft.server.level.ServerPlayer sp
                    && level instanceof net.minecraft.server.level.ServerLevel sl) {
                com.vel5id.hexerei.soul.DreamEntry.onDrink(sp, sl);
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

    /** Lists the brew's effects like a vanilla potion tooltip (name [+ potency] + duration, category-colored). */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Brew brew = brewOf(stack);
        if (brew == null) {
            super.appendHoverText(stack, context, tooltip, flag);
            return;
        }
        for (BrewEffect e : brew.effects()) {
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(e.effectId())).orElse(null);
            if (effect == null) {
                continue; // unknown effect id — skip rather than render a broken line
            }
            MutableComponent line = Component.translatable(effect.value().getDescriptionId());
            if (e.amplifier() > 0) {
                line = Component.translatable("potion.withAmplifier", line,
                        Component.translatable("potion.potency." + e.amplifier()));
            }
            line = Component.translatable("potion.withDuration", line, formatDuration(e.durationTicks()));
            tooltip.add(line.withStyle(effect.value().getCategory().getTooltipFormatting()));
        }
    }

    /** m:ss from ticks (20 ticks/second), matching vanilla potion duration display. */
    private static Component formatDuration(int ticks) {
        int seconds = ticks / 20;
        return Component.literal(String.format("%d:%02d", seconds / 60, seconds % 60));
    }
}
