package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.block.crop.MistletoeBlock;
import com.vel5id.hexerei.block.crop.WitchCropBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** One row per Hexerei crop -> registers its block + seed item; produce items live in HexereiItems. */
public final class HexereiCrops {
    private HexereiCrops() {}

    public static final List<DeferredHolder<Block, Block>> CROP_BLOCKS = new ArrayList<>();
    public static final List<DeferredHolder<Item, Item>> SEED_ITEMS = new ArrayList<>();
    private static final Map<String, DeferredHolder<Item, Item>> SEED_BY_CROP = new HashMap<>();

    private static BlockBehaviour.Properties cropProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission()
                .randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY);
    }

    /** seed item id: mindrake -> mindrake_bulb, garlic -> garlic, else seeds_<crop>. */
    private static String seedName(String crop) {
        return switch (crop) {
            case "mindrake" -> "mindrake_bulb";
            case "garlic" -> "garlic";
            default -> "seeds_" + crop;
        };
    }

    private static DeferredHolder<Item, Item> seedFor(String crop) {
        return SEED_BY_CROP.get(crop);
    }

    /** The seed item registered for a crop (e.g. "belladonna" -> seeds_belladonna). */
    public static Item seedItem(String crop) {
        return SEED_BY_CROP.get(crop).get();
    }

    private static DeferredHolder<Block, Block> crop(String name, int maxAge, boolean water, boolean big,
            boolean mindrake, boolean snowbell, boolean wormwood,
            @Nullable Supplier<Item> produce, @Nullable Supplier<Item> bonus) {
        Supplier<Item> seedSup = () -> seedFor(name).get();
        Supplier<Item> produceSup = produce != null ? produce : seedSup; // mindrake/garlic: produce == seed
        DeferredHolder<Block, Block> block = HexereiBlocks.BLOCKS.register(name,
                () -> new WitchCropBlock(maxAge, water, big, mindrake, snowbell, wormwood,
                        seedSup, produceSup, bonus, cropProps()));
        DeferredHolder<Item, Item> seed = HexereiItems.ITEMS.register(seedName(name),
                () -> new ItemNameBlockItem(block.get(), new Item.Properties()));
        SEED_BY_CROP.put(name, seed);
        CROP_BLOCKS.add(block);
        SEED_ITEMS.add(seed);
        return block;
    }

    public static final DeferredHolder<Block, Block> BELLADONNA = crop("belladonna", 4, false, true, false, false, false,
            () -> HexereiItems.BELLADONNA_FLOWER.get(), null);
    public static final DeferredHolder<Block, Block> MANDRAKE = crop("mandrake", 4, false, true, false, false, false,
            () -> HexereiItems.MANDRAKE_ROOT.get(), null);
    public static final DeferredHolder<Block, Block> ARTICHOKE = crop("artichoke", 4, true, true, false, false, false,
            () -> HexereiItems.ARTICHOKE.get(), null);
    // Renamed snowbell -> hellebore ("Морозник"). The 6th constructor arg (the WitchCropBlock "snowbell"
    // boolean) is the hellebore bonus-drop flag: kept true so the +20% icy_needle bonus drop still fires.
    // Mature produce stays vanilla snowball + 20% icy_needle (icy_needle is a brew ingredient — do not retheme).
    public static final DeferredHolder<Block, Block> HELLEBORE = crop("hellebore", 4, false, true, false, true, false,
            () -> Items.SNOWBALL, () -> HexereiItems.ICY_NEEDLE.get());
    public static final DeferredHolder<Block, Block> WORMWOOD = crop("wormwood", 4, false, true, false, false, true,
            () -> HexereiItems.WORMWOOD.get(), null);
    public static final DeferredHolder<Block, Block> MINDRAKE = crop("mindrake", 4, false, false, true, false, false,
            null, null);
    public static final DeferredHolder<Block, Block> WOLFSBANE = crop("wolfsbane", 7, false, false, false, false, false,
            () -> HexereiItems.WOLFSBANE.get(), null);
    public static final DeferredHolder<Block, Block> GARLIC = crop("garlic", 5, false, true, false, false, false,
            null, null);

    // --- slice H: new farmland crops (all via crop(...), seed item auto-registered) ---
    // crowseye / celandine: standard bonemeal-big farmland crops, maxAge 4.
    public static final DeferredHolder<Block, Block> CROWSEYE = crop("crowseye", 4, false, true, false, false, false,
            () -> HexereiItems.CROWSEYE_BERRY.get(), null);
    public static final DeferredHolder<Block, Block> CELANDINE = crop("celandine", 4, false, true, false, false, false,
            () -> HexereiItems.CELANDINE.get(), null);
    // hops: reuses the wormwood "tall/self-stacking" behaviour (wormwood=true) — mature bottom grows an upper segment.
    public static final DeferredHolder<Block, Block> HOPS = crop("hops", 4, false, true, false, false, true,
            () -> HexereiItems.HOPS.get(), null);
    // sandwort: big=false ⇒ bonemeal gives +1 per use (slow, hardy desert herb).
    public static final DeferredHolder<Block, Block> SANDWORT = crop("sandwort", 4, false, false, false, false, false,
            () -> HexereiItems.SANDWORT.get(), null);

    // mistletoe: a WitchCropBlock subclass with log/leaf placement, so it cannot use the farmland-only
    // crop(...) helper (which hard-codes new WitchCropBlock). Registered via cropSubclass so it still
    // joins CROP_BLOCKS/SEED_ITEMS/SEED_BY_CROP — creative tab + the instanceof WitchCropBlock altar synergy fire.
    public static final DeferredHolder<Block, Block> MISTLETOE = cropSubclass("mistletoe",
            (seedSup, produceSup) -> new MistletoeBlock(4, false, true, false, false, false,
                    seedSup, produceSup, null, cropProps()),
            () -> HexereiItems.MISTLETOE_SPRIG.get());

    /** Functional factory for a WitchCropBlock subclass given its (seed, produce) suppliers. */
    @FunctionalInterface
    private interface CropFactory {
        Block create(Supplier<Item> seed, Supplier<Item> produce);
    }

    /** Like crop(...) but for a pre-built WitchCropBlock subclass with a non-default placement rule. */
    private static DeferredHolder<Block, Block> cropSubclass(String name, CropFactory factory,
            @Nullable Supplier<Item> produce) {
        Supplier<Item> seedSup = () -> seedFor(name).get();
        Supplier<Item> produceSup = produce != null ? produce : seedSup;
        DeferredHolder<Block, Block> block = HexereiBlocks.BLOCKS.register(name,
                () -> factory.create(seedSup, produceSup));
        DeferredHolder<Item, Item> seed = HexereiItems.ITEMS.register(seedName(name),
                () -> new ItemNameBlockItem(block.get(), new Item.Properties()));
        SEED_BY_CROP.put(name, seed);
        CROP_BLOCKS.add(block);
        SEED_ITEMS.add(seed);
        return block;
    }

    /** Force class-load so the static crop registrations run. Call from the mod constructor. */
    public static void init() {}
}
