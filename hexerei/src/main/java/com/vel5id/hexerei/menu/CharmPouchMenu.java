package com.vel5id.hexerei.menu;

import com.vel5id.hexerei.item.CharmPouchItem;
import com.vel5id.hexerei.registry.HexereiMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Container menu for the {@link CharmPouchItem}: 3 charm-only slots backed by the held pouch stack's NBT,
 * plus the player inventory. Slot contents sync over the vanilla menu channel — no custom packet. Edits are
 * written back into the pouch stack's NBT when the menu closes.
 */
public class CharmPouchMenu extends AbstractContainerMenu {
    private static final int CHARM_SLOTS = CharmPouchItem.SLOTS;

    private final ItemStack pouch;
    private final InteractionHand hand;
    private final ItemStackHandler handler;

    /** Client-side factory: the opening packet carries which hand holds the pouch. */
    public static CharmPouchMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        InteractionHand hand = buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return new CharmPouchMenu(id, inv, inv.player.getItemInHand(hand), hand);
    }

    public CharmPouchMenu(int id, Inventory inv, ItemStack pouch, InteractionHand hand) {
        super(HexereiMenus.CHARM_POUCH.get(), id);
        this.pouch = pouch;
        this.hand = hand;
        this.handler = CharmPouchItem.readHandler(pouch);

        // charm row (centered), then the player inventory + hotbar at vanilla offsets.
        for (int i = 0; i < CHARM_SLOTS; i++) {
            addSlot(new SlotItemHandler(handler, i, 62 + i * 18, 33));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        // the pouch must remain the exact held stack — guards against it being swapped or dropped while open.
        return player.getItemInHand(hand) == pouch && pouch.getItem() instanceof CharmPouchItem;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        CharmPouchItem.writeHandler(pouch, handler);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < CHARM_SLOTS) {
            // pouch -> player inventory
            if (!moveItemStackTo(stack, CHARM_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // player inventory -> pouch (slot validity rejects non-charms and nested pouches)
            if (!moveItemStackTo(stack, 0, CHARM_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
