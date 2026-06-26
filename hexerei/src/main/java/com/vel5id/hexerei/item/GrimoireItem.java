package com.vel5id.hexerei.item;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import vazkii.patchouli.api.PatchouliAPI;

/** The Grimoire: right-click opens the Hexerei guide book. Patchouli renders it client-side from a server-issued open. */
public class GrimoireItem extends Item {
    public static final ResourceLocation BOOK_ID = ResourceLocation.fromNamespaceAndPath(HexereiMod.MODID, "grimoire");

    public GrimoireItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // openBookGUI is server-initiated; Patchouli sends its own open packet to that player's client.
        if (player instanceof ServerPlayer serverPlayer) {
            PatchouliAPI.get().openBookGUI(serverPlayer, BOOK_ID);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
