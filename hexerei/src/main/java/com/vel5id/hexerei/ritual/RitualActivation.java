package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.RelativePowerSource;
import com.vel5id.hexerei.registry.HexereiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

/** Activates a ritual at a circle center: complete circle + sacrifice item + altar power -> rite. */
public final class RitualActivation {
    private RitualActivation() {}

    private static final double SACRIFICE_RADIUS = 2.5;

    public enum Result { SUCCESS, NO_RECIPE, NO_POWER }

    public static Result tryPerform(ServerLevel level, BlockPos center) {
        // Contract: the center must be a ritual sigil block (self-contained for any caller).
        if (!level.getBlockState(center).is(HexereiBlocks.RITUAL_SIGIL.get())) {
            return Result.NO_RECIPE;
        }
        java.util.function.Predicate<BlockPos> isGlyph =
                p -> level.getBlockState(p).is(HexereiBlocks.RUNE.get());

        // Horizontal reach only — keep the sacrifice on the circle's Y-layer (not in a hole / floating above).
        double r = SACRIFICE_RADIUS;
        AABB box = new AABB(center.getX() - r, center.getY(), center.getZ() - r,
                center.getX() + 1 + r, center.getY() + 1.5, center.getZ() + 1 + r);

        boolean anyMatched = false;
        for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.getItem().isEmpty())) {
            ItemStack stack = ie.getItem();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            Optional<RitualRecipe> match = RitualRecipes.match(isGlyph, center, id);
            if (match.isEmpty()) {
                continue;
            }
            anyMatched = true;
            RitualRecipe recipe = match.get();
            if (!payPower(level, center, recipe.powerCost())) {
                continue; // another sacrifice in range might be affordable
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                ie.discard();
            } else {
                ie.setItem(stack);
            }
            try {
                recipe.rite().perform(level, center);
            } catch (Exception e) {
                HexereiMod.LOGGER.error("Rite {} failed to perform", recipe.nameKey(), e);
            }
            return Result.SUCCESS;
        }
        return anyMatched ? Result.NO_POWER : Result.NO_RECIPE;
    }

    /** Atomically debit {@code cost} from the first in-range altar that can pay it. */
    private static boolean payPower(ServerLevel level, BlockPos center, int cost) {
        if (cost <= 0) {
            return true;
        }
        for (RelativePowerSource r : AltarPowerManager.get(level).query(level, center)) {
            if (r.source().consumePower(cost)) {
                return true;
            }
        }
        return false;
    }
}
