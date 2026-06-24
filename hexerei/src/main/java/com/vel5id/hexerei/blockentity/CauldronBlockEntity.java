package com.vel5id.hexerei.blockentity;

import com.vel5id.hexerei.brewing.Brew;
import com.vel5id.hexerei.brewing.BrewColor;
import com.vel5id.hexerei.brewing.BrewRecipes;
import com.vel5id.hexerei.item.BrewItem;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.RelativePowerSource;
import com.vel5id.hexerei.registry.HexereiBlockEntities;
import com.vel5id.hexerei.registry.HexereiTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** The Witch's Cauldron: water + heat + herb ingredients + nearby altar power -> a collectable brew. */
public class CauldronBlockEntity extends BlockEntity {
    private static final int BOIL_TICKS = 100; // ~5s
    public static final int MAX_WATER = 3;

    private int waterLevel = 0;
    private int heatTicks = 0;
    private final List<String> ingredients = new ArrayList<>();
    private int color = BrewColor.WATER;
    private boolean powered = false;
    private long ticks = 0;

    public CauldronBlockEntity(BlockPos pos, BlockState state) {
        super(HexereiBlockEntities.CAULDRON.get(), pos, state);
    }

    public boolean isFilled() {
        return waterLevel >= MAX_WATER;
    }

    public boolean isBoiling() {
        return heatTicks >= BOIL_TICKS && isFilled();
    }

    public int getColor() {
        return color;
    }

    public int getWaterLevel() {
        return waterLevel;
    }

    public boolean isPowered() {
        return powered;
    }

    @Nullable
    public Brew readyBrew() {
        return BrewRecipes.match(ingredients).orElse(null);
    }

    public boolean isReady() {
        return readyBrew() != null;
    }

    private int requiredPower() {
        Brew b = readyBrew();
        return b != null ? b.power() : 0;
    }

    // ---- ticking ----
    public static void serverTick(Level level, BlockPos pos, BlockState state, CauldronBlockEntity be) {
        be.ticks++;
        boolean sync = false;

        BlockState below = level.getBlockState(pos.below());
        boolean heated = be.isFilled() && be.isHeatSource(below);
        if (heated) {
            if (be.heatTicks < BOIL_TICKS && ++be.heatTicks == BOIL_TICKS) {
                sync = true;
            }
        } else if (be.heatTicks > 0) {
            be.heatTicks = 0;
            sync = true;
        }

        if (be.isBoiling()) {
            if (be.absorbIngredients(level)) {
                sync = true;
            }
            if (be.ticks % 20L == 7L) {
                boolean was = be.powered;
                int need = be.requiredPower();
                if (need <= 0) {
                    be.powered = true;
                } else if (level instanceof ServerLevel server) {
                    be.powered = AltarPowerManager.get(server).query(level, pos).stream()
                            .anyMatch(r -> r.source().getCurrentPower() >= need);
                } else {
                    be.powered = false;
                }
                if (was != be.powered) {
                    sync = true;
                }
            }
        } else if (be.powered) {
            be.powered = false;
            sync = true;
        }

        if (sync) {
            be.setChanged();
            be.sync();
        }
    }

    private boolean isHeatSource(BlockState below) {
        if (below.is(HexereiTags.CAULDRON_HEAT_SOURCES)) {
            return true;
        }
        return below.getBlock() instanceof CampfireBlock
                && below.hasProperty(CampfireBlock.LIT) && below.getValue(CampfireBlock.LIT);
    }

    private boolean absorbIngredients(Level level) {
        boolean changed = false;
        AABB box = new AABB(worldPosition).deflate(0.1);
        for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.getItem().isEmpty())) {
            ItemStack stack = ie.getItem();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (BrewRecipes.canAccept(ingredients, id)) {
                ingredients.add(id);
                recomputeColor();
                stack.shrink(1);
                if (stack.isEmpty()) {
                    ie.discard();
                } else {
                    // pop the leftover stack back out so it isn't trapped inside the cauldron
                    ie.setItem(stack);
                    ie.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5);
                    ie.setDeltaMovement(0.0, 0.2, 0.0);
                }
                changed = true;
            }
        }
        return changed;
    }

    private void recomputeColor() {
        List<Integer> cols = new ArrayList<>();
        for (String id : ingredients) {
            Integer c = BrewRecipes.INGREDIENT_COLORS.get(id);
            if (c != null) {
                cols.add(c);
            }
        }
        color = BrewColor.blend(cols);
    }

    // ---- block interactions (called from CauldronBlock.use) ----
    public boolean fillWater() {
        if (isFilled()) {
            return false;
        }
        waterLevel = MAX_WATER;
        ingredients.clear();
        color = BrewColor.WATER;
        setChanged();
        sync();
        return true;
    }

    public boolean hasWater() {
        return waterLevel > 0;
    }

    /** Empty the cauldron — lets a player rinse out a wrong/incomplete brew mix. */
    public void drain() {
        waterLevel = 0;
        ingredients.clear();
        color = BrewColor.WATER;
        heatTicks = 0;
        powered = false;
        setChanged();
        sync();
    }

    /** Try to collect the ready brew. Returns the brew item if boiling + powered + power consumed, else null. */
    @Nullable
    public ItemStack collectBrew() {
        if (level == null || level.isClientSide) {
            return null;
        }
        Brew brew = readyBrew();
        if (brew == null || !isBoiling()) {
            return null;
        }
        int need = brew.power();
        if (need > 0) {
            // consumePower is authoritative (don't gate on the cached `powered` flag, which lags up to 20 ticks);
            // try each in-range altar in distance order until one can pay the full cost.
            if (!(level instanceof ServerLevel server)) {
                return null;
            }
            boolean paid = false;
            for (RelativePowerSource r : AltarPowerManager.get(server).query(level, worldPosition)) {
                if (r.source().consumePower(need)) {
                    paid = true;
                    break;
                }
            }
            if (!paid) {
                return null;
            }
        }
        ItemStack result = BrewItem.of(brew);
        waterLevel = 0;
        ingredients.clear();
        color = BrewColor.WATER;
        heatTicks = 0;
        powered = false;
        setChanged();
        sync();
        return result;
    }

    // ---- NBT + sync ----
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Water", waterLevel);
        tag.putInt("Heat", heatTicks);
        tag.putInt("Color", color);
        tag.putBoolean("Powered", powered);
        ListTag list = new ListTag();
        for (String s : ingredients) {
            list.add(StringTag.valueOf(s));
        }
        tag.put("Ingredients", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        waterLevel = tag.getInt("Water");
        heatTicks = tag.getInt("Heat");
        color = tag.contains("Color") ? tag.getInt("Color") : BrewColor.WATER;
        powered = tag.getBoolean("Powered");
        ingredients.clear();
        ListTag list = tag.getList("Ingredients", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ingredients.add(list.getString(i));
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            syncState();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Reconcile the blockstate FILLED/BOILING booleans to the BE's numeric truth (server-only). */
    private void syncState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState st = getBlockState();
        if (!(st.getBlock() instanceof com.vel5id.hexerei.block.cauldron.CauldronBlock)) {
            return;
        }
        boolean wantFilled = isFilled();
        boolean wantBoiling = isBoiling();
        if (st.getValue(com.vel5id.hexerei.block.cauldron.CauldronBlock.FILLED) != wantFilled
                || st.getValue(com.vel5id.hexerei.block.cauldron.CauldronBlock.BOILING) != wantBoiling) {
            level.setBlock(worldPosition,
                    st.setValue(com.vel5id.hexerei.block.cauldron.CauldronBlock.FILLED, wantFilled)
                      .setValue(com.vel5id.hexerei.block.cauldron.CauldronBlock.BOILING, wantBoiling), 3);
        }
    }

    /** Client-only ambient particles while boiling — bubbles tinted by the brew color, plus rising steam. */
    public void clientTick(Level level, BlockPos pos, BlockState state) {
        if (!isBoiling()) {
            return;
        }
        long gt = level.getGameTime();
        var rnd = level.random;
        if (gt % 4L == 0L) {
            float[] c = CauldronVisuals.rgb(color);
            int n = rnd.nextInt(3);
            for (int i = 0; i < n; i++) {
                double px = pos.getX() + 0.30 + rnd.nextDouble() * 0.40;
                double pz = pos.getZ() + 0.30 + rnd.nextDouble() * 0.40;
                double py = pos.getY() + 0.80;
                level.addParticle(com.vel5id.hexerei.registry.HexereiParticles.CAULDRON_BUBBLE.get(),
                        px, py, pz, c[0], c[1], c[2]);
            }
        }
        if (gt % 10L == 0L) {
            double px = pos.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.5;
            double pz = pos.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.5;
            double py = pos.getY() + 0.95;
            level.addParticle(com.vel5id.hexerei.registry.HexereiParticles.CAULDRON_STEAM.get(),
                    px, py, pz, 0.0, 0.03 + rnd.nextDouble() * 0.02, 0.0);
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }
}
