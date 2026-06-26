package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.menu.CharmPouchMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HexereiMenus {
    private HexereiMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, HexereiMod.MODID);

    // IMenuTypeExtension.create wires the client-side factory that reads the opening packet's extra data.
    public static final DeferredHolder<MenuType<?>, MenuType<CharmPouchMenu>> CHARM_POUCH = MENUS.register("charm_pouch",
            () -> IMenuTypeExtension.create(CharmPouchMenu::fromNetwork));
}
