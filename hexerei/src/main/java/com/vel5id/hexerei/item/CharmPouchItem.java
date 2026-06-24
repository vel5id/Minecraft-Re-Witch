package com.vel5id.hexerei.item;

import com.vel5id.hexerei.menu.CharmPouchMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * A pouch that holds up to {@value #SLOTS} combat charms. Right-click to open its GUI; while the pouch is
 * carried, each contained charm grants its buff (driven server-side by {@code CharmTickHandler}). Contents
 * live in the pouch stack's own NBT, so they travel with the item — no BlockEntity, no SavedData.
 */
public class CharmPouchItem extends Item {
    public static final int SLOTS = 3;
    private static final String KEY_ITEMS = "hexerei:Items";

    public CharmPouchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean isCharm(ItemStack stack) {
        return stack.getItem() instanceof CharmItem;
    }

    /** A fresh 3-slot handler that accepts only charms (never a nested pouch), one per slot. */
    public static ItemStackHandler createHandler() {
        return new ItemStackHandler(SLOTS) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return isCharm(stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
    }

    /** Reads the pouch's contained charms into a handler (empty if the pouch has none). */
    public static ItemStackHandler readHandler(ItemStack pouch) {
        ItemStackHandler handler = createHandler();
        if (pouch.hasTag() && pouch.getTag().contains(KEY_ITEMS)) {
            handler.deserializeNBT(pouch.getTag().getCompound(KEY_ITEMS));
        }
        return handler;
    }

    public static void writeHandler(ItemStack pouch, ItemStackHandler handler) {
        pouch.getOrCreateTag().put(KEY_ITEMS, handler.serializeNBT());
    }

    /** The non-empty charm stacks currently in the pouch. */
    public static List<ItemStack> contents(ItemStack pouch) {
        ItemStackHandler handler = readHandler(pouch);
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            boolean offhand = hand == InteractionHand.OFF_HAND;
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new CharmPouchMenu(id, inv, stack, hand), stack.getHoverName());
            NetworkHooks.openScreen(serverPlayer, provider, buf -> buf.writeBoolean(offhand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // ---- aggregate charge bar (vanilla durability-bar API) ----

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !contents(stack).isEmpty();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * chargeFraction(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x9B59B6; // witch purple
    }

    /** Mean charge across the filled slots, in [0, 1]; 0 when empty. */
    private static float chargeFraction(ItemStack pouch) {
        List<ItemStack> charms = contents(pouch);
        if (charms.isEmpty()) {
            return 0.0F;
        }
        int total = 0;
        for (ItemStack charm : charms) {
            total += CharmItem.getCharge(charm);
        }
        return total / (float) (CharmCharge.MAX_CHARGE * charms.size());
    }
}
