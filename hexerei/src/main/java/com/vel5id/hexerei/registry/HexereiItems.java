package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.item.BrewItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HexereiItems {
    private HexereiItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HexereiMod.MODID);

    public static final RegistryObject<Item> ALTAR = ITEMS.register("altar",
            () -> new BlockItem(HexereiBlocks.ALTAR.get(), new Item.Properties()));

    public static final RegistryObject<Item> CAULDRON = ITEMS.register("cauldron",
            () -> new BlockItem(HexereiBlocks.CAULDRON.get(), new Item.Properties()));

    // Drinkable brew (its specific brew identity lives in NBT, set when collected from a cauldron).
    public static final RegistryObject<Item> BREW = ITEMS.register("brew",
            () -> new BrewItem(new Item.Properties().stacksTo(16)));

    // --- crop produce / ingredients (seeds + dual seed/produce items live in HexereiCrops) ---
    public static final RegistryObject<Item> BELLADONNA_FLOWER = ITEMS.register("belladonna_flower",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MANDRAKE_ROOT = ITEMS.register("mandrake_root",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WORMWOOD = ITEMS.register("wormwood",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WOLFSBANE = ITEMS.register("wolfsbane",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ICY_NEEDLE = ITEMS.register("icy_needle",
            () -> new Item(new Item.Properties()));
    // Artichoke is edible. food=20/sat=0.0 is intentionally very high — [UNVERIFIED], may retune.
    public static final RegistryObject<Item> ARTICHOKE = ITEMS.register("artichoke",
            () -> new Item(new Item.Properties().food(
                    new FoodProperties.Builder().nutrition(20).saturationMod(0.0F).build())));
}
