package com.vel5id.hexerei.item;

import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.ritual.RitualCircle;
import com.vel5id.hexerei.ritual.RitualRecipe;
import com.vel5id.hexerei.ritual.RitualRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Ritual Chalk: right-click a Ritual Circle center to draw glyphs for the selected rite. */
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

        if (level.getBlockState(clicked).is(HexereiBlocks.RITUAL_CIRCLE.get())) {
            if (!level.isClientSide) {
                ItemStack stack = ctx.getItemInHand();
                RitualRecipe rite = getSelectedRecipe(stack);
                List<BlockPos> ring = (rite != null)
                        ? rite.circleSize().ringPositions(clicked)
                        : RitualCircle.smallRing(clicked);
                for (BlockPos ringPos : ring) {
                    if (canPlaceGlyph(level, ringPos)) {
                        level.setBlock(ringPos, HexereiBlocks.RITUAL_GLYPH.get().defaultBlockState(), 3);
                        damage(ctx, player, hand);
                        level.playSound(null, ringPos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
                        break;
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // otherwise draw a single glyph on top of the clicked block
        BlockPos above = clicked.above();
        if (canPlaceGlyph(level, above)) {
            if (!level.isClientSide) {
                level.setBlock(above, HexereiBlocks.RITUAL_GLYPH.get().defaultBlockState(), 3);
                damage(ctx, player, hand);
                level.playSound(null, above, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        RitualRecipe rite = getSelectedRecipe(stack);
        if (rite != null) {
            tooltip.add(Component.translatable("item.hexerei.ritual_chalk.rite",
                    Component.translatable(rite.nameKey())).withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("item.hexerei.ritual_chalk.circle",
                    rite.circleSize().ringPositions(BlockPos.ZERO).size()).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("item.hexerei.ritual_chalk.tip2").withStyle(ChatFormatting.GRAY));
    }

    /** Returns the currently selected rite from the item's NBT, or the first recipe as default. */
    public static RitualRecipe getSelectedRecipe(ItemStack stack) {
        if (stack.hasTag()) {
            String id = stack.getTag().getString("hexerei:rite");
            RitualRecipe r = RitualRecipes.BY_ID.get(id);
            if (r != null) return r;
        }
        return RitualRecipes.ALL.isEmpty() ? null : RitualRecipes.ALL.get(0);
    }

    /** Place the small glyph ring around {@code center} where each cell is air over a sturdy block. Returns the count placed. */
    public static int drawCircleAt(Level level, BlockPos center) {
        int placed = 0;
        for (BlockPos ring : RitualCircle.smallRing(center)) {
            if (canPlaceGlyph(level, ring)) {
                level.setBlock(ring, HexereiBlocks.RITUAL_GLYPH.get().defaultBlockState(), 3);
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
            ctx.getItemInHand().hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        }
    }
}
