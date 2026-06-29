package com.vel5id.hexerei.block.crop;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Self-contained Hexerei herb-crop block (per-crop AGE 0..maxAge). */
public class WitchCropBlock extends BushBlock implements BonemealableBlock {
    private static final Map<Integer, IntegerProperty> AGE_CACHE = new HashMap<>();
    private static int STASH_MAX; // set in stash() before super(); block registration is single-threaded
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);

    public static IntegerProperty ageProperty(int maxAge) {
        return AGE_CACHE.computeIfAbsent(maxAge, m -> IntegerProperty.create("age", 0, m));
    }

    private static Properties stash(int maxAge, Properties p) {
        STASH_MAX = maxAge;
        return p;
    }

    private final int maxAge;
    private final boolean water;
    private final boolean bonemealBig;
    private final boolean mindrake;
    private final boolean snowbell;
    private final boolean wormwood;
    private final Supplier<Item> seed;
    private final Supplier<Item> produce;
    @Nullable private final Supplier<Item> bonus; // icy needle for snowbell

    public WitchCropBlock(int maxAge, boolean water, boolean bonemealBig, boolean mindrake,
                          boolean snowbell, boolean wormwood,
                          Supplier<Item> seed, Supplier<Item> produce, @Nullable Supplier<Item> bonus,
                          Properties props) {
        super(stash(maxAge, props));
        this.maxAge = maxAge;
        this.water = water;
        this.bonemealBig = bonemealBig;
        this.mindrake = mindrake;
        this.snowbell = snowbell;
        this.wormwood = wormwood;
        this.seed = seed;
        this.produce = produce;
        this.bonus = bonus;
        registerDefaultState(stateDefinition.any().setValue(ageProperty(maxAge), 0));
    }

    // 1.21 requires a block codec; these crops carry per-crop item Suppliers from registration and are
    // never serialized via the block codec, so a self-returning stub satisfies the contract.
    @Override
    public MapCodec<? extends BushBlock> codec() {
        return simpleCodec(p -> this);
    }

    public int maxAge() {
        return maxAge;
    }

    public IntegerProperty age() {
        return ageProperty(maxAge);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ageProperty(STASH_MAX));
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState ground, BlockGetter w, BlockPos pos) {
        if (water) {
            return ground.is(Blocks.WATER)
                    || ground.getFluidState().is(Fluids.WATER)
                    || ground.getFluidState().is(Fluids.FLOWING_WATER);
        }
        // Vanilla-like: plant only on tilled soil (farmland); crops do NOT stack.
        // Wormwood additionally stands on itself so its mature upward-growth segment survives.
        return ground.is(Blocks.FARMLAND) || (wormwood && ground.is(this));
    }

    // Forge's BushBlock.canSurvive routes through soil.canSustainPlant (IPlantable), which lets
    // dirt/grass sustain "plains" plants — bypassing mayPlaceOn. Override to enforce our rule.
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }

    @Override
    public boolean isRandomlyTicking(BlockState s) {
        return s.getValue(age()) < maxAge || wormwood;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
        if (level.getRawBrightness(pos.above(), 0) < 9) {
            return;
        }
        int a = state.getValue(age());
        if (a < maxAge) {
            float f = CropGrowthRate.compute(level, pos, this, mindrake);
            if (CropGrowth.shouldGrow(r, f)) {
                level.setBlock(pos, state.setValue(age(), a + 1), 2);
            }
        } else if (wormwood) {
            BlockPos up = pos.above();
            if (level.isEmptyBlock(up) && !(level.getBlockState(pos.below()).getBlock() instanceof WitchCropBlock)) {
                level.setBlock(up, upperBlockState(), 3);
            }
        }
    }

    /** State placed as the mature upward (second) segment. Base = a plain copy; a tall crop with a
     *  distinct top texture (e.g. hops) overrides this to mark the segment so its model differs. */
    protected BlockState upperBlockState() {
        return defaultBlockState();
    }

    // ---- BonemealableBlock ----
    @Override
    public boolean isValidBonemealTarget(LevelReader w, BlockPos p, BlockState s) {
        return s.getValue(age()) < maxAge;
    }

    @Override
    public boolean isBonemealSuccess(Level w, RandomSource r, BlockPos p, BlockState s) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel w, RandomSource r, BlockPos p, BlockState s) {
        int next = CropGrowth.bonemealIncrease(r, s.getValue(age()), maxAge, bonemealBig);
        w.setBlock(p, s.setValue(age(), next), 2);
    }

    // ---- drops (mandrake entity-spawn deferred) ----
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> out = new ArrayList<>();
        ServerLevel level = builder.getLevel();
        RandomSource r = level.getRandom();
        boolean mature = state.getValue(age()) >= maxAge;
        ItemStack tool = builder.getOptionalParameter(LootContextParams.TOOL);
        int fortune = 0;
        if (tool != null) {
            Holder<Enchantment> fortuneEnch = level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.FORTUNE);
            fortune = EnchantmentHelper.getItemEnchantmentLevel(fortuneEnch, tool);
        }
        CropDrops.Roll roll = CropDrops.roll(r, mature, fortune, mindrake, snowbell);
        for (int i = 0; i < roll.seeds(); i++) {
            out.add(new ItemStack(seed.get()));
        }
        for (int i = 0; i < roll.produce(); i++) {
            out.add(new ItemStack(produce.get()));
        }
        if (roll.icyNeedle() && bonus != null) {
            out.add(new ItemStack(bonus.get()));
        }
        return out;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader w, BlockPos p, BlockState s) {
        return new ItemStack(seed.get());
    }
}
