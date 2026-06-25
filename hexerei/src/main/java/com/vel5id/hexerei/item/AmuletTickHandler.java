package com.vel5id.hexerei.item;

import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Correspondence;
import com.vel5id.hexerei.soul.Disposition;
import com.vel5id.hexerei.soul.Disturbance;
import com.vel5id.hexerei.soul.HexereiCapabilities;
import com.vel5id.hexerei.soul.PlayerSoulData;
import com.vel5id.hexerei.soul.SealRef;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives worn amulets once per second (server-only, called from {@code HexereiLevelEvents} on the 20-tick
 * boundary): each sealed amulet in a carried {@link CharmPouchItem} draws soul-debt (grinding its seal)
 * and grants its domain effect. Replaces the retired {@code CharmTickHandler}'s charge/recharge model —
 * there is no altar recharge; the cost is the witch's own debt (Article III: power = debt).
 *
 * <p>Also reconciles {@link PlayerSoulData#amulets()} and {@code totalDebt} with the pouch contents, so
 * the dream/living-world readers (future slices) always see the witch's currently-worn bonds.
 */
public final class AmuletTickHandler {
    private AmuletTickHandler() {}

    /** Refreshed every second; the ~2s window means an amulet's buff lapses within ~1s of unequip/break. */
    private static final int EFFECT_DURATION = 40;

    public static void tick(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            processPlayer(level, player);
        }
    }

    /** Process one player's carried pouch, if any. Exposed so GameTests can drive a mock player directly. */
    public static void processPlayer(ServerLevel level, Player player) {
        ItemStack pouch = findPouch(player);
        if (pouch.isEmpty()) {
            player.getCapability(HexereiCapabilities.PLAYER_SOUL).ifPresent(psd -> {
                if (!psd.amulets().isEmpty()) {
                    psd.amulets().clear();
                    psd.setTotalDebt(0f);
                }
            });
            return;
        }

        ItemStackHandler handler = CharmPouchItem.readHandler(pouch);
        List<Bond> worn = new ArrayList<>();
        boolean changed = false;

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!AmuletItem.isSealed(stack)) {
                continue;
            }
            Bond bond = AmuletItem.readBond(stack);
            if (bond == null) {
                continue;
            }

            // --- cost: debt accrues; resentment (unpaid debt over loyalty) grinds the seal ---
            Disposition d = bond.disposition();
            float newDebt = d.debt() + SealedAmulet.debtDelta();
            float resentment = SealedAmulet.resentmentFor(newDebt, d.loyalty());
            float integrity = (bond.seal() != null ? bond.seal().integrity() : 0f)
                    + SealedAmulet.integrityDelta(resentment);

            if (SealedAmulet.shouldBreak(integrity)) {
                Disturbance.add(level, new ChunkPos(player.blockPosition()),
                        bond.domain(), SealedAmulet.BREAK_DISTURBANCE);
                level.playSound(null, player.blockPosition(),
                        SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.7f);
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.4, 0.6, 0.4, 0.02);
                handler.setStackInSlot(i, ItemStack.EMPTY);
                changed = true;
                continue;
            }

            Bond updated = bond.withDisposition(
                            new Disposition(newDebt, resentment, d.fear(), d.loyalty()))
                    .withSeal(new SealRef(integrity));
            AmuletItem.writeBond(stack, updated);
            handler.setStackInSlot(i, stack);
            changed = true;
            worn.add(updated);

            // --- effect: the spirit's nature, when its condition is met ---
            SealedAmulet.AmuletEffect fx = SealedAmulet.effectFor(updated.domain());
            if (fx != null && SealedAmulet.playerCondition(fx, player.getHealth())) {
                applyEffect(level, player, fx);
            }
        }

        if (changed) {
            CharmPouchItem.writeHandler(pouch, handler);
        }

        // --- reconcile PlayerSoulData with the currently-worn bonds (Модель §3) ---
        player.getCapability(HexereiCapabilities.PLAYER_SOUL).ifPresent(psd -> {
            psd.amulets().clear();
            for (Bond b : worn) {
                psd.wearAmulet(b.bondId());
            }
            float total = 0f;
            for (Bond b : worn) {
                total += b.disposition().debt();
            }
            psd.setTotalDebt(total);
        });
    }

    private static ItemStack findPouch(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof CharmPouchItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void applyEffect(ServerLevel level, Player player, SealedAmulet.AmuletEffect fx) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(fx.effectId()));
        if (effect == null) {
            return;
        }
        if (fx.mode() == ActiveMode.AURA_DEBUFF) {
            AABB box = player.getBoundingBox().inflate(fx.param());
            for (Monster mob : level.getEntitiesOfClass(Monster.class, box)) {
                mob.addEffect(instance(effect, fx));
            }
        } else {
            player.addEffect(instance(effect, fx));
        }
    }

    // ambient, hidden particles, visible icon — mirrors the retired charm feedback rule.
    private static MobEffectInstance instance(MobEffect effect, SealedAmulet.AmuletEffect fx) {
        return new MobEffectInstance(effect, EFFECT_DURATION, fx.amplifier(), true, false, true);
    }
}
