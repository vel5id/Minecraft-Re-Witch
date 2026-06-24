package com.vel5id.hexerei.item;

import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.RelativePowerSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Drives every carried charm pouch once per second: drains active charms, recharges them near a powered
 * altar (debiting that altar), and applies each effective charm's buff. Server-authoritative — called from
 * {@code HexereiLevelEvents.onLevelTick} on the 20-tick boundary, never on the client.
 */
public final class CharmTickHandler {
    private CharmTickHandler() {}

    // Refreshed every second; the 2s window means a charm's buff lapses within ~1s of going dormant.
    private static final int EFFECT_DURATION = 40;

    public static void tick(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            processPlayer(level, player);
        }
    }

    /** Process one player's carried pouch, if any. Exposed so GameTests can drive a mock player directly. */
    public static void processPlayer(ServerLevel level, Player player) {
        ItemStack pouch = findPouch(player);
        if (!pouch.isEmpty()) {
            tickPouch(level, player, pouch);
        }
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

    private static void tickPouch(ServerLevel level, Player player, ItemStack pouch) {
        ItemStackHandler handler = CharmPouchItem.readHandler(pouch);
        boolean altarAvailable = altarAvailable(level, player);
        boolean changed = false;

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack charm = handler.getStackInSlot(i);
            CharmDef def = CharmItem.defOf(charm);
            if (def == null) {
                continue;
            }
            boolean wantActive = CharmCharge.playerCondition(def, player.getHealth());
            int before = CharmItem.getCharge(charm);
            int after = CharmCharge.nextCharge(before, wantActive, altarAvailable);
            if (after > before && !debitAltar(level, player, CharmCharge.RECHARGE_POWER_PER_UNIT * (after - before))) {
                after = before; // altar couldn't pay — no free recharge
            }
            if (after != before) {
                CharmItem.setCharge(charm, after);
                handler.setStackInSlot(i, charm);
                changed = true;
            }
            if (CharmCharge.isEffective(after, wantActive)) {
                applyEffect(level, player, def);
            }
        }

        if (changed) {
            CharmPouchItem.writeHandler(pouch, handler);
        }
    }

    /** True if some in-range altar holds at least one second's worth of recharge power. */
    private static boolean altarAvailable(ServerLevel level, Player player) {
        float need = CharmCharge.RECHARGE_POWER_PER_UNIT * CharmCharge.RECHARGE;
        return AltarPowerManager.get(level).query(level, player.blockPosition()).stream()
                .anyMatch(r -> r.source().getCurrentPower() >= need);
    }

    /** Atomically debit {@code cost} from the first in-range altar that can pay it. */
    private static boolean debitAltar(ServerLevel level, Player player, float cost) {
        for (RelativePowerSource r : AltarPowerManager.get(level).query(level, player.blockPosition())) {
            if (r.source().consumePower(cost)) {
                return true;
            }
        }
        return false;
    }

    private static void applyEffect(ServerLevel level, Player player, CharmDef def) {
        MobEffect effect = CharmItem.resolveEffect(def);
        if (effect == null) {
            return;
        }
        if (def.mode() == ActiveMode.AURA_DEBUFF) {
            AABB box = player.getBoundingBox().inflate(def.param());
            for (Monster mob : level.getEntitiesOfClass(Monster.class, box)) {
                mob.addEffect(instance(effect, def));
            }
        } else {
            player.addEffect(instance(effect, def));
        }
    }

    // ambient, hidden particles, visible icon — see the design's feedback rule.
    private static MobEffectInstance instance(MobEffect effect, CharmDef def) {
        return new MobEffectInstance(effect, EFFECT_DURATION, def.amplifier(), true, false, true);
    }
}
