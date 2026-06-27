package com.vel5id.hexerei.soul;

import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;

/**
 * The dream's {@code seal} verb over a player's inventory: nothing material crosses the threshold.
 * On entry the waking inventory is snapshotted into {@link DreamState} and the live inventory is
 * emptied; on every wake path it is restored verbatim and anything acquired in the dream is dropped.
 *
 * <p>Stateless. Methods take {@link Player} (not {@code ServerPlayer}) so GameTests can exercise them
 * with a mock player, mirroring {@link DreamEntry#onDrink}. {@code Inventory#save/#load} use the
 * player's own {@code registryAccess()} internally, so no {@code HolderLookup.Provider} is needed.
 */
public final class DreamInventory {
    private DreamInventory() {}

    /** Snapshot the inventory into {@code st}, then empty it. Snapshot is stored BEFORE the clear. */
    public static void sealInto(Player player, DreamState st) {
        ListTag snapshot = player.getInventory().save(new ListTag());
        st.seal(snapshot);               // persisted in DreamState first…
        player.getInventory().clearContent();   // …then the live inventory is emptied
    }

    /** Restore the snapshot verbatim (Inventory#load clears all slots first) and unseal. No-op if unsealed. */
    public static void restoreFrom(Player player, DreamState st) {
        if (!st.sealed()) return;
        player.getInventory().load(st.invSnapshot());
        st.unseal();
    }
}
