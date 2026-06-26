package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.blockentity.AltarBlockEntity;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.BloodMoonData;
import com.vel5id.hexerei.power.IPowerSource;
import com.vel5id.hexerei.power.RelativePowerSource;
import com.vel5id.hexerei.registry.HexereiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Optional;

/** Activates a ritual at a circle center: complete circle + sacrifice item + altar power -> rite. */
public final class RitualActivation {
    private RitualActivation() {}

    private static final double SACRIFICE_RADIUS = 2.5;

    public enum Result { SUCCESS, FAILED, NO_RECIPE, NO_POWER }

    public static Result tryPerform(ServerLevel level, BlockPos center) {
        return tryPerform(level, center, (java.util.UUID) null);
    }

    /** Activation attributed to {@code caster} — exposes the activating player to the rite via {@link RitualContext}. */
    public static Result tryPerform(ServerLevel level, BlockPos center,
                                   @Nullable net.minecraft.world.entity.player.Player caster) {
        return tryPerform(level, center, caster == null ? null : caster.getUUID());
    }

    public static Result tryPerform(ServerLevel level, BlockPos center, @Nullable java.util.UUID casterUuid) {
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
            IPowerSource funder = payPower(level, center, recipe.powerCost());
            // A priced rite must be funded; a free rite (cost<=0) proceeds even with no altar (funder may be null).
            if (recipe.powerCost() > 0 && funder == null) {
                continue; // another sacrifice in range might be affordable
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                ie.discard();
            } else {
                ie.setItem(stack);
            }

            // Resolve the rite against the PLACE (Грамматика §6-7): its Act = sacrifice reagent + the
            // ring's rune domains; a domain already disturbed resists. A resisted/misaligned cast still
            // spent the sacrifice — it BOTCHES (feeds the loop with more disturbance), never a free no-op.
            com.vel5id.hexerei.soul.Act act = buildRitualAct(level, center, recipe, id);
            float reach = sacrificeReach(id); // OVERREACH = the witch's demand (her sacrifice), NOT the rune ring
            com.vel5id.hexerei.soul.Correspondence dom = com.vel5id.hexerei.soul.RitualResolver.dominantDomain(act);
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(center);
            float domDist = dom == null ? 0f : com.vel5id.hexerei.soul.Disturbance.domainTotal(level, cp, dom);
            float totDist = com.vel5id.hexerei.soul.Disturbance.total(level, cp);
            if (!com.vel5id.hexerei.soul.RitualResolver.isSuccess(
                    com.vel5id.hexerei.soul.RitualResolver.resolve(act, domDist, totDist, reach).outcome())) {
                if (dom != null) {
                    com.vel5id.hexerei.soul.Disturbance.add(level, cp, dom,
                            com.vel5id.hexerei.soul.RitualResolver.BOTCH_DISTURBANCE);
                }
                level.playSound(null, center, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 0.6f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                        center.getX() + 0.5, center.getY() + 0.6, center.getZ() + 0.5, 20, 0.6, 0.3, 0.6, 0.02);
                return Result.FAILED;
            }

            // Compose the multipliers: funding altar artefact x lunar phase x active blood moon.
            float artefactTaint = funder instanceof AltarBlockEntity altar ? altar.taintMultiplier() : 1f;
            float artefactEffect = funder instanceof AltarBlockEntity altar ? altar.effectMultiplier() : 1f;
            LunarPhase phase = LunarPhase.fromIndex(level.dimensionType().moonPhase(level.getDayTime()));
            boolean bloodMoon = BloodMoonData.get(level).isActive();
            float taintMul = artefactTaint * phase.taintMul() * (bloodMoon ? BloodMoonData.BLOOD_TAINT_MUL : 1f);
            float effectMul = artefactEffect * phase.effectMul() * (bloodMoon ? BloodMoonData.RITUAL_EFFECT_MUL : 1f);
            RitualContext.begin(taintMul, effectMul, casterUuid);
            try {
                recipe.rite().perform(level, center);
                maybeIgniteBloodMoon(level, recipe, phase);
            } catch (Exception e) {
                HexereiMod.LOGGER.error("Rite {} failed to perform", recipe.nameKey(), e);
            } finally {
                RitualContext.end();
            }
            return Result.SUCCESS;
        }
        return anyMatched ? Result.NO_POWER : Result.NO_RECIPE;
    }

    /** Assemble the rite's Act from the sacrifice reagent + each ring rune's domain glyph (Грамматика §1). */
    private static com.vel5id.hexerei.soul.Act buildRitualAct(ServerLevel level, BlockPos center,
                                                              RitualRecipe recipe, String sacrificeId) {
        java.util.List<com.vel5id.hexerei.soul.ReagentDescriptor> reagents = new java.util.ArrayList<>();
        net.minecraft.resources.ResourceLocation sid = net.minecraft.resources.ResourceLocation.tryParse(sacrificeId);
        com.vel5id.hexerei.soul.ReagentDescriptor sac = sid == null ? null
                : com.vel5id.hexerei.soul.ReagentRegistry.get(sid);
        if (sac != null) {
            reagents.add(sac);
        }
        for (BlockPos rp : recipe.circleSize().ringPositions(center)) {
            net.minecraft.world.level.block.state.BlockState bs = level.getBlockState(rp);
            if (bs.is(HexereiBlocks.RUNE.get())) {
                com.vel5id.hexerei.soul.Correspondence d =
                        com.vel5id.hexerei.block.ritual.RuneBlock.symbolOf(bs).domain();
                reagents.add(new com.vel5id.hexerei.soul.ReagentDescriptor(d, 0f, 0.2f, 0f, 0.3f)); // a rune writes its domain
            }
        }
        return com.vel5id.hexerei.soul.ActAssembler.assemble(reagents);
    }

    /** The caster's reach for the OVERREACH test: the sacrifice reagent's magnitude (0 if it carries no spirit). */
    private static float sacrificeReach(String sacrificeId) {
        net.minecraft.resources.ResourceLocation sid = net.minecraft.resources.ResourceLocation.tryParse(sacrificeId);
        com.vel5id.hexerei.soul.ReagentDescriptor sac =
                sid == null ? null : com.vel5id.hexerei.soul.ReagentRegistry.get(sid);
        return sac == null ? 0f : sac.magnitude();
    }

    /** A small chance (bumped on a new moon) that performing any non-eclipse rite ignites a blood moon. */
    private static void maybeIgniteBloodMoon(ServerLevel level, RitualRecipe recipe, LunarPhase phase) {
        BloodMoonData data = BloodMoonData.get(level);
        if (data.isActive() || recipe.id().equals(RitualRecipes.ECLIPSE.id())) {
            return; // already a blood moon, or the eclipse rite which ignites deterministically
        }
        float chance = phase.isNew() ? 0.05f : 0.02f;
        if (level.getRandom().nextFloat() < chance) {
            data.ignite(level);
        }
    }

    /**
     * Atomically debit {@code cost} from the first in-range altar that can pay it, returning that altar so the
     * caller can read its multipliers. A free rite ({@code cost <= 0}) is attributed to the nearest in-range
     * source if one exists. Returns {@code null} when no source paid (priced) or none is in range (free) — the
     * caller distinguishes these by the recipe cost.
     */
    @Nullable
    private static IPowerSource payPower(ServerLevel level, BlockPos center, int cost) {
        if (cost <= 0) {
            for (RelativePowerSource r : AltarPowerManager.get(level).query(level, center)) {
                return r.source();
            }
            return null;
        }
        for (RelativePowerSource r : AltarPowerManager.get(level).query(level, center)) {
            if (r.source().consumePower(cost)) {
                return r.source();
            }
        }
        return null;
    }
}
