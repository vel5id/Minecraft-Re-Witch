package com.vel5id.hexerei.ritual;

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
        boolean circleComplete = RitualCircle.isSmallComplete(
                p -> level.getBlockState(p).is(HexereiBlocks.RITUAL_GLYPH.get()), center);

        AABB box = new AABB(center).inflate(SACRIFICE_RADIUS);
        for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.getItem().isEmpty())) {
            ItemStack stack = ie.getItem();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            Optional<RitualRecipe> match = RitualRecipes.match(circleComplete, id);
            if (match.isEmpty()) {
                continue;
            }
            RitualRecipe recipe = match.get();

            if (recipe.powerCost() > 0) {
                boolean paid = false;
                for (RelativePowerSource r : AltarPowerManager.get(level).query(level, center)) {
                    if (r.source().consumePower(recipe.powerCost())) {
                        paid = true;
                        break;
                    }
                }
                if (!paid) {
                    return Result.NO_POWER;
                }
            }

            stack.shrink(1);
            if (stack.isEmpty()) {
                ie.discard();
            } else {
                ie.setItem(stack);
            }
            recipe.rite().perform(level, center);
            return Result.SUCCESS;
        }
        return Result.NO_RECIPE;
    }
}
