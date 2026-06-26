package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.item.AmuletItem;
import com.vel5id.hexerei.item.ArtefactDefs;
import com.vel5id.hexerei.item.ArtefactItem;
import com.vel5id.hexerei.item.BrewItem;
import com.vel5id.hexerei.item.CharmPouchItem;
import com.vel5id.hexerei.item.GrimoireItem;
import com.vel5id.hexerei.item.TaglockItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HexereiItems {
    private HexereiItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, HexereiMod.MODID);

    public static final DeferredHolder<Item, Item> ALTAR = ITEMS.register("altar",
            () -> new BlockItem(HexereiBlocks.ALTAR.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CAULDRON = ITEMS.register("cauldron",
            () -> new BlockItem(HexereiBlocks.CAULDRON.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> RUNE = ITEMS.register("rune",
            () -> new BlockItem(HexereiBlocks.RUNE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> RITUAL_SIGIL = ITEMS.register("ritual_sigil",
            () -> new BlockItem(HexereiBlocks.RITUAL_SIGIL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> RITUAL_CHALK = ITEMS.register("ritual_chalk",
            () -> new com.vel5id.hexerei.item.RitualChalkItem(new Item.Properties().durability(64)));

    public static final DeferredHolder<Item, Item> TAINTED_GROUND = ITEMS.register("tainted_ground",
            () -> new BlockItem(HexereiBlocks.TAINTED_GROUND.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> CHARRED_STONE = ITEMS.register("charred_stone",
            () -> new BlockItem(HexereiBlocks.CHARRED_STONE.get(), new Item.Properties()));

    // Drinkable brew (its specific brew identity lives in NBT, set when collected from a cauldron).
    public static final DeferredHolder<Item, Item> BREW = ITEMS.register("brew",
            () -> new BrewItem(new Item.Properties().stacksTo(16)));

    // --- crop produce / ingredients (seeds + dual seed/produce items live in HexereiCrops) ---
    public static final DeferredHolder<Item, Item> BELLADONNA_FLOWER = ITEMS.register("belladonna_flower",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> MANDRAKE_ROOT = ITEMS.register("mandrake_root",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> WORMWOOD = ITEMS.register("wormwood",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> WOLFSBANE = ITEMS.register("wolfsbane",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> ICY_NEEDLE = ITEMS.register("icy_needle",
            () -> new Item(new Item.Properties()));
    // Artichoke is edible. food=20/sat=0.0 is intentionally very high — [UNVERIFIED], may retune.
    public static final DeferredHolder<Item, Item> ARTICHOKE = ITEMS.register("artichoke",
            () -> new Item(new Item.Properties().food(
                    new FoodProperties.Builder().nutrition(20).saturationModifier(0.0F).build())));

    // --- slice H: new herb/mushroom produce (non-edible brew reagents; brews designed in overhaul-brews) ---
    public static final DeferredHolder<Item, Item> CROWSEYE_BERRY = ITEMS.register("crowseye_berry",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> CELANDINE = ITEMS.register("celandine",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> HOPS = ITEMS.register("hops",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SANDWORT = ITEMS.register("sandwort",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> MISTLETOE_SPRIG = ITEMS.register("mistletoe_sprig",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> GLOWING_SPORE = ITEMS.register("glowing_spore",
            () -> new Item(new Item.Properties()));

    // --- slice H: BlockItems for the decoration + mushroom blocks (these blocks ARE the reagents) ---
    public static final DeferredHolder<Item, Item> BLOOD_MOSS = ITEMS.register("blood_moss",
            () -> new BlockItem(HexereiBlocks.BLOOD_MOSS.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> ZEVANTY = ITEMS.register("zevanty",
            () -> new BlockItem(HexereiBlocks.ZEVANTY.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PUFFBALL = ITEMS.register("puffball",
            () -> new BlockItem(HexereiBlocks.PUFFBALL.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> WEBCAP = ITEMS.register("webcap",
            () -> new BlockItem(HexereiBlocks.WEBCAP.get(), new Item.Properties()));

    // --- sealed amulets (a bond carried in NBT; obtained from a sealing rite, worn in a charm pouch) ---
    public static final DeferredHolder<Item, Item> CHARM_POUCH = ITEMS.register("charm_pouch",
            () -> new CharmPouchItem(new Item.Properties()));
    public static final DeferredHolder<Item, Item> AMULET = ITEMS.register("amulet",
            () -> new AmuletItem(new Item.Properties().stacksTo(1)));

    // --- taglock: binds to a player (right-click) to target a curse rite at a circle ---
    public static final DeferredHolder<Item, Item> TAGLOCK = ITEMS.register("taglock",
            () -> new TaglockItem(new Item.Properties().stacksTo(16)));

    // --- altar artefacts (placed in the altar's single slot; scale a rite's taint/effect, see ArtefactDef) ---
    public static final DeferredHolder<Item, Item> BONE_CHARM = ITEMS.register("bone_charm",
            () -> new ArtefactItem(new Item.Properties(), ArtefactDefs.BONE_CHARM));
    public static final DeferredHolder<Item, Item> WAX_POPPET = ITEMS.register("wax_poppet",
            () -> new ArtefactItem(new Item.Properties(), ArtefactDefs.WAX_POPPET));
    public static final DeferredHolder<Item, Item> OBSIDIAN_SKULL = ITEMS.register("obsidian_skull",
            () -> new ArtefactItem(new Item.Properties(), ArtefactDefs.OBSIDIAN_SKULL));

    // The in-game guide book (opens the Patchouli "grimoire" book).
    public static final DeferredHolder<Item, Item> GRIMOIRE = ITEMS.register("grimoire",
            () -> new GrimoireItem(new Item.Properties()));
}
