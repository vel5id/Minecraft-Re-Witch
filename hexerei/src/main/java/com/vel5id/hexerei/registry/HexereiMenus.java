package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.menu.CharmPouchMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HexereiMenus {
    private HexereiMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, HexereiMod.MODID);

    // IForgeMenuType.create wires the client-side factory that reads the opening packet's extra data.
    public static final RegistryObject<MenuType<CharmPouchMenu>> CHARM_POUCH = MENUS.register("charm_pouch",
            () -> IForgeMenuType.create(CharmPouchMenu::fromNetwork));
}
