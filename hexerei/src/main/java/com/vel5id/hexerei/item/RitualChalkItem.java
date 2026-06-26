package com.vel5id.hexerei.item;

import com.vel5id.hexerei.blockentity.RitualSigilBlockEntity;
import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.registry.HexereiDataComponents;
import com.vel5id.hexerei.ritual.LunarPhase;
import com.vel5id.hexerei.ritual.RitualCircle;
import com.vel5id.hexerei.ritual.RitualRecipe;
import com.vel5id.hexerei.ritual.RitualRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.List;

/** Ritual Chalk: right-click a Ritual Sigil to draw the selected rite's rune ring and bind it. */
public class RitualChalkItem extends Item {
    public RitualChalkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        InteractionHand hand = ctx.getHand();

        if (level.getBlockState(clicked).is(HexereiBlocks.RITUAL_SIGIL.get())) {
            if (!level.isClientSide) {
                drawAndBind(ctx, level, clicked, player, hand);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Right-click an existing rune to cycle its symbol (the diegetic domain glyph).
        var cs = level.getBlockState(clicked);
        if (cs.is(HexereiBlocks.RUNE.get())) {
            if (!level.isClientSide) {
                int next = (cs.getValue(com.vel5id.hexerei.block.ritual.RuneBlock.SYMBOL) + 1)
                        % com.vel5id.hexerei.block.ritual.RuneBlock.SYMBOL_COUNT;
                level.setBlock(clicked, cs.setValue(com.vel5id.hexerei.block.ritual.RuneBlock.SYMBOL, next), 3);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Runes only exist as part of a sigil ring — no off-sigil free-draw fallback.
        return InteractionResult.PASS;
    }

    /**
     * Plain right-click on a sigil: draw all ring runes for the selected rite and bind the rite to
     * the sigil's BlockEntity. A same rite+size redraw acts as repair (re-places any missing runes,
     * no warning); a different rite/size is rejected with an action-bar message.
     */
    private void drawAndBind(UseOnContext ctx, Level level, BlockPos sigilPos, @Nullable Player player, InteractionHand hand) {
        RitualRecipe rite = getSelectedRecipe(ctx.getItemInHand());
        if (rite == null) {
            return;
        }

        RitualSigilBlockEntity be = sigilEntity(level, sigilPos);

        // GUARD: a bound sigil rejects any draw whose (riteId, size) differs from the bound pair.
        if (be != null && be.isBound()
                && (!be.boundRiteId().equals(rite.id()) || be.boundSize() != rite.circleSize())) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("hexerei.ritual.destroy_first"), true);
            }
            level.playSound(null, sigilPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 0.8F);
            return;
        }

        // Draw every missing ring cell in one use (repair when same rite+size).
        boolean placedAny = false;
        for (BlockPos ringPos : rite.circleSize().ringPositions(sigilPos)) {
            if (canPlaceGlyph(level, ringPos)) {
                level.setBlock(ringPos, HexereiBlocks.RUNE.get().defaultBlockState(), 3);
                placedAny = true;
            }
        }
        if (placedAny) {
            damage(ctx, player, hand);
            level.playSound(null, sigilPos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
        }

        // Bind once the ring is complete; partial draws stay unbound and re-drawable.
        if (be != null && !be.isBound()
                && rite.circleSize().isComplete(p -> level.getBlockState(p).is(HexereiBlocks.RUNE.get()), sigilPos)) {
            be.bind(rite.id(), rite.circleSize());
        }
    }

    @Nullable
    private static RitualSigilBlockEntity sigilEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof RitualSigilBlockEntity sigil ? sigil : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Level level = context.level();
        RitualRecipe rite = getSelectedRecipe(stack);
        if (rite != null) {
            tooltip.add(Component.translatable("item.hexerei.ritual_chalk.rite",
                    Component.translatable(rite.nameKey())).withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable(rite.circleSize().circleLabelKey(),
                    rite.circleSize().ringPositions(BlockPos.ZERO).size()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (level != null) {
            // The current moon and what it does to rites — read live (the client level has the same moon formula).
            LunarPhase phase = LunarPhase.fromIndex(level.dimensionType().moonPhase(level.getDayTime()));
            ChatFormatting color = phase.effectMul() > 1f ? ChatFormatting.GREEN
                    : phase.effectMul() < 1f ? ChatFormatting.RED : ChatFormatting.GRAY;
            MutableComponent moon = Component.translatable("item.hexerei.ritual_chalk.moon",
                    Component.translatable(phase.nameKey()), Component.translatable(phase.effectKey()))
                    .withStyle(color);
            if (phase.isFull() || phase.isNew()) {
                moon = moon.withStyle(ChatFormatting.BOLD);
            }
            tooltip.add(moon);
        }
        tooltip.add(Component.translatable("item.hexerei.ritual_chalk.tip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.hexerei.ritual_chalk.tip3").withStyle(ChatFormatting.GRAY));
    }

    /** Returns the currently selected rite from the item's component, or the first recipe as default. */
    public static RitualRecipe getSelectedRecipe(ItemStack stack) {
        return RitualRecipes.fromId(stack.getOrDefault(HexereiDataComponents.SELECTED_RITE.get(), ""));
    }

    /** Place the small glyph ring around {@code center} where each cell is air over a sturdy block. Returns the count placed. */
    public static int drawCircleAt(Level level, BlockPos center) {
        int placed = 0;
        for (BlockPos ring : RitualCircle.smallRing(center)) {
            if (canPlaceGlyph(level, ring)) {
                level.setBlock(ring, HexereiBlocks.RUNE.get().defaultBlockState(), 3);
                placed++;
            }
        }
        return placed;
    }

    private static boolean canPlaceGlyph(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static void damage(UseOnContext ctx, @Nullable Player player, InteractionHand hand) {
        if (player != null && !player.getAbilities().instabuild) {
            ctx.getItemInHand().hurtAndBreak(1, player,
                    hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
    }
}
