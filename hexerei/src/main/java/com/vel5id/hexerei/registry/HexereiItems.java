package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.item.ArtefactDefs;
import com.vel5id.hexerei.item.ArtefactItem;
import com.vel5id.hexerei.item.BrewItem;
import com.vel5id.hexerei.item.CharmDefs;
import com.vel5id.hexerei.item.CharmItem;
import com.vel5id.hexerei.item.CharmPouchItem;
import com.vel5id.hexerei.item.GrimoireItem;
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

    public static final RegistryObject<Item> RUNE = ITEMS.register("rune",
            () -> new BlockItem(HexereiBlocks.RUNE.get(), new Item.Properties()));

    public static final RegistryObject<Item> RITUAL_SIGIL = ITEMS.register("ritual_sigil",
            () -> new BlockItem(HexereiBlocks.RITUAL_SIGIL.get(), new Item.Properties()));

    public static final RegistryObject<Item> RITUAL_CHALK = ITEMS.register("ritual_chalk",
            () -> new com.vel5id.hexerei.item.RitualChalkItem(new Item.Properties().durability(64)));

    public static final RegistryObject<Item> TAINTED_GROUND = ITEMS.register("tainted_ground",
            () -> new BlockItem(HexereiBlocks.TAINTED_GROUND.get(), new Item.Properties()));

    public static final RegistryObject<Item> CHARRED_STONE = ITEMS.register("charred_stone",
            () -> new BlockItem(HexereiBlocks.CHARRED_STONE.get(), new Item.Properties()));

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

    // --- combat charms (carried in a charm pouch; per-charm charge lives in NBT, see CharmItem) ---
    public static final RegistryObject<Item> CHARM_POUCH = ITEMS.register("charm_pouch",
            () -> new CharmPouchItem(new Item.Properties()));
    public static final RegistryObject<Item> WARD_CHARM = ITEMS.register("ward_charm",
            () -> new CharmItem(new Item.Properties(), CharmDefs.WARD));
    public static final RegistryObject<Item> BLOODLUST_CHARM = ITEMS.register("bloodlust_charm",
            () -> new CharmItem(new Item.Properties(), CharmDefs.BLOODLUST));
    public static final RegistryObject<Item> HEXBANE_CHARM = ITEMS.register("hexbane_charm",
            () -> new CharmItem(new Item.Properties(), CharmDefs.HEXBANE));

    // --- altar artefacts (placed in the altar's single slot; scale a rite's taint/effect, see ArtefactDef) ---
    public static final RegistryObject<Item> BONE_CHARM = ITEMS.register("bone_charm",
            () -> new ArtefactItem(new Item.Properties(), ArtefactDefs.BONE_CHARM));
    public static final RegistryObject<Item> WAX_POPPET = ITEMS.register("wax_poppet",
            () -> new ArtefactItem(new Item.Properties(), ArtefactDefs.WAX_POPPET));
    public static final RegistryObject<Item> OBSIDIAN_SKULL = ITEMS.register("obsidian_skull",
            () -> new ArtefactItem(new Item.Properties(), ArtefactDefs.OBSIDIAN_SKULL));

    // The in-game guide book (opens the Patchouli "grimoire" book).
    public static final RegistryObject<Item> GRIMOIRE = ITEMS.register("grimoire",
            () -> new GrimoireItem(new Item.Properties()));
}
