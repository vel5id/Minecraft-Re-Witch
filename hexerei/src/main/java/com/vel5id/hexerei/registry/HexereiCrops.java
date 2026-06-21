package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.block.crop.WitchCropBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** One row per Hexerei crop -> registers its block + seed item; produce items live in HexereiItems. */
public final class HexereiCrops {
    private HexereiCrops() {}

    public static final List<RegistryObject<Block>> CROP_BLOCKS = new ArrayList<>();
    public static final List<RegistryObject<Item>> SEED_ITEMS = new ArrayList<>();
    private static final Map<String, RegistryObject<Item>> SEED_BY_CROP = new HashMap<>();

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

    private static RegistryObject<Item> seedFor(String crop) {
        return SEED_BY_CROP.get(crop);
    }

    /** The seed item registered for a crop (e.g. "belladonna" -> seeds_belladonna). */
    public static Item seedItem(String crop) {
        return SEED_BY_CROP.get(crop).get();
    }

    private static RegistryObject<Block> crop(String name, int maxAge, boolean water, boolean big,
            boolean mindrake, boolean snowbell, boolean wormwood,
            @Nullable Supplier<Item> produce, @Nullable Supplier<Item> bonus) {
        Supplier<Item> seedSup = () -> seedFor(name).get();
        Supplier<Item> produceSup = produce != null ? produce : seedSup; // mindrake/garlic: produce == seed
        RegistryObject<Block> block = HexereiBlocks.BLOCKS.register(name,
                () -> new WitchCropBlock(maxAge, water, big, mindrake, snowbell, wormwood,
                        seedSup, produceSup, bonus, cropProps()));
        RegistryObject<Item> seed = HexereiItems.ITEMS.register(seedName(name),
                () -> new ItemNameBlockItem(block.get(), new Item.Properties()));
        SEED_BY_CROP.put(name, seed);
        CROP_BLOCKS.add(block);
        SEED_ITEMS.add(seed);
        return block;
    }

    public static final RegistryObject<Block> BELLADONNA = crop("belladonna", 4, false, true, false, false, false,
            () -> HexereiItems.BELLADONNA_FLOWER.get(), null);
    public static final RegistryObject<Block> MANDRAKE = crop("mandrake", 4, false, true, false, false, false,
            () -> HexereiItems.MANDRAKE_ROOT.get(), null);
    public static final RegistryObject<Block> ARTICHOKE = crop("artichoke", 4, true, true, false, false, false,
            () -> HexereiItems.ARTICHOKE.get(), null);
    public static final RegistryObject<Block> SNOWBELL = crop("snowbell", 4, false, true, false, true, false,
            () -> Items.SNOWBALL, () -> HexereiItems.ICY_NEEDLE.get());
    public static final RegistryObject<Block> WORMWOOD = crop("wormwood", 4, false, true, false, false, true,
            () -> HexereiItems.WORMWOOD.get(), null);
    public static final RegistryObject<Block> MINDRAKE = crop("mindrake", 4, false, false, true, false, false,
            null, null);
    public static final RegistryObject<Block> WOLFSBANE = crop("wolfsbane", 7, false, false, false, false, false,
            () -> HexereiItems.WOLFSBANE.get(), null);
    public static final RegistryObject<Block> GARLIC = crop("garlic", 5, false, true, false, false, false,
            null, null);

    /** Force class-load so the static crop registrations run. Call from the mod constructor. */
    public static void init() {}
}
