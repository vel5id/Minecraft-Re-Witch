package com.vel5id.hexerei.blockentity;

import com.vel5id.hexerei.block.AltarBlock;
import com.vel5id.hexerei.block.AltarFormation;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.AltarPowerTable;
import com.vel5id.hexerei.power.IPowerSource;
import com.vel5id.hexerei.power.PowerSource;
import com.vel5id.hexerei.registry.HexereiBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Block entity backing the Hexerei altar: tracks its multiblock core, scans the surrounding world for natural power sources, and stores/recharges accumulated power. */
public class AltarBlockEntity extends BlockEntity implements IPowerSource {
    private static final int SCAN_DISTANCE = 14;          // -> 29^3 cube scanned around the core
    private static final long SCAN_THROTTLE_TICKS = 100;
    private static final float BASE_POWER_PER_UPDATE = 10.0F;

    @Nullable private BlockPos core;   // null = not part of a complete altar
    private float power;
    private float maxPower;
    private int powerScale = 1;
    private int rechargeScale = 1;
    private int rangeScale = 1;
    private int enhancementLevel = 0;
    private long ticks = 0;
    private long lastPowerUpdate = 0;

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(HexereiBlockEntities.ALTAR.get(), pos, state);
    }

    private boolean isCore() {
        return core != null && core.equals(worldPosition);
    }

    public BlockPos corePos() {
        return core != null ? core : worldPosition;
    }

    // ---- ticking ----
    public static void serverTick(Level level, BlockPos pos, BlockState state, AltarBlockEntity be) {
        be.ticks++;
        if (!be.isCore()) {
            return;
        }
        float maxScaled = be.maxPower * be.powerScale;
        if (be.power < maxScaled) {
            if (be.ticks % 20L == 0L) {
                be.power = (int) Math.min(be.power + BASE_POWER_PER_UPDATE * be.rechargeScale, maxScaled);
                be.sync();
            }
        } else if (be.power > maxScaled && be.ticks % 20L == 0L) {
            be.power = maxScaled;
            be.sync();
        }

        // Sync taint level to blockstate every 40 ticks (core only)
        if (be.ticks % 40 == 0 && be.isCore() && level instanceof net.minecraft.server.level.ServerLevel sl) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(pos);
            com.vel5id.hexerei.power.TaintLevel tl = com.vel5id.hexerei.power.ChunkTaintData.get(sl).getLevel(cp);
            int taintLvl = tl.ordinal();
            BlockState cur = level.getBlockState(pos);
            if (cur.getBlock() instanceof com.vel5id.hexerei.block.AltarBlock && cur.getValue(com.vel5id.hexerei.block.AltarBlock.TAINT_LEVEL) != taintLvl) {
                level.setBlock(pos, cur.setValue(com.vel5id.hexerei.block.AltarBlock.TAINT_LEVEL, taintLvl), 3);
                com.vel5id.hexerei.network.HexereiNetwork.sendTaintSync(sl, cp);
            }
        }
    }

    public void revalidateAndUpdate() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe) {
            coreBe.updatePower(true);
        }
    }

    // ---- multiblock formation ----
    public void updateMultiblock(@Nullable BlockPos exclude) {
        if (level == null || level.isClientSide) {
            return;
        }
        Set<BlockPos> altars = new HashSet<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> stack = new ArrayDeque<>();
        stack.add(worldPosition);
        while (!stack.isEmpty()) {
            BlockPos p = stack.poll();
            if (!seen.add(p)) {
                continue;
            }
            if (p.equals(exclude)) {
                continue;
            }
            if (!(level.getBlockState(p).getBlock() instanceof AltarBlock)) {
                continue;
            }
            altars.add(p);
            stack.add(p.north());
            stack.add(p.south());
            stack.add(p.east());
            stack.add(p.west());
        }
        BlockPos newCore = AltarFormation.findCore(altars, worldPosition);
        for (BlockPos p : altars) {
            if (level.getBlockEntity(p) instanceof AltarBlockEntity be) {
                be.setCore(newCore);
            }
        }
        if (exclude != null && level.getBlockEntity(exclude) instanceof AltarBlockEntity be) {
            be.setCore(null);
        }
    }

    private void setCore(@Nullable BlockPos newCore) {
        this.core = newCore;
        if (level instanceof ServerLevel server) {
            if (isCore()) {
                updatePower(false);
                AltarPowerManager.get(server).register(this);
            } else if (newCore == null) {
                AltarPowerManager.get(server).unregister(this);
                power = 0;
                maxPower = 0;
                powerScale = 1;
                rechargeScale = 1;
                rangeScale = 1;
                enhancementLevel = 0;
            }
        }
        if (level != null) {
            BlockState st = getBlockState();
            boolean joined = newCore != null;
            if (st.getBlock() instanceof AltarBlock && st.getValue(AltarBlock.ALTAR_JOINED) != joined) {
                level.setBlock(worldPosition, st.setValue(AltarBlock.ALTAR_JOINED, joined), 3);
            }
        }
        setChanged();
        sync();
    }

    // ---- power scan ----
    private void updatePower(boolean throttle) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (throttle && !(ticks - lastPowerUpdate <= 0 || ticks - lastPowerUpdate > SCAN_THROTTLE_TICKS)) {
            return;
        }
        lastPowerUpdate = ticks;

        Map<Block, PowerSource> table = new HashMap<>();
        for (Map.Entry<String, AltarPowerTable.Entry> e : AltarPowerTable.VANILLA.entrySet()) {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(e.getKey()));
            if (b != null && b != Blocks.AIR) {
                table.put(b, new PowerSource(e.getValue().factor(), e.getValue().limit()));
            }
        }

        BlockPos c = corePos();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = c.getY() - SCAN_DISTANCE; y <= c.getY() + SCAN_DISTANCE; y++) {
            for (int z = c.getZ() - SCAN_DISTANCE; z <= c.getZ() + SCAN_DISTANCE; z++) {
                for (int x = c.getX() - SCAN_DISTANCE; x <= c.getX() + SCAN_DISTANCE; x++) {
                    p.set(x, y, z);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState bs = level.getBlockState(p);
                    if (bs.isAir()) {
                        continue;
                    }
                    Block b = bs.getBlock();
                    PowerSource src = table.get(b);
                    if (src == null) {
                        src = resolveDynamic(bs, b, table);
                    }
                    if (src != null) {
                        src.increment();
                    }
                }
            }
        }

        float newMax = 0;
        for (PowerSource s : table.values()) {
            newMax += s.getPower();
        }
        if (newMax != maxPower) {
            maxPower = newMax;
            setChanged();
            sync();
        }
    }

    /** Tag/instanceof sources resolved lazily and memoised into the table. */
    @Nullable
    private PowerSource resolveDynamic(BlockState bs, Block b, Map<Block, PowerSource> table) {
        AltarPowerTable.Entry e = null;
        if (bs.is(BlockTags.SAPLINGS)) {
            e = AltarPowerTable.TAG_SAPLING;
        } else if (bs.is(BlockTags.LOGS)) {
            e = AltarPowerTable.TAG_LOG;
        } else if (bs.is(BlockTags.LEAVES)) {
            e = AltarPowerTable.TAG_LEAVES;
        } else if (bs.is(BlockTags.SMALL_FLOWERS)) {
            e = AltarPowerTable.FLOWER;          // 4/30 for small flowers
        } else if (b instanceof com.vel5id.hexerei.block.crop.WitchCropBlock) {
            e = AltarPowerTable.CROP;            // 4/20 for Hexerei herb crops
        } else if (b instanceof FlowerBlock || b instanceof CropBlock) {
            e = AltarPowerTable.CATCHALL;        // 2/4
        }
        // FUTURE: additional power-source blocks (LEAVES 4/50, LOG 3/100, mosses, high-tier reagents, ...)
        if (e == null) {
            return null;
        }
        PowerSource s = new PowerSource(e.factor(), e.limit());
        table.put(b, s);
        return s;
    }

    // ---- IPowerSource ----
    @Override
    public Level getWorld() {
        return level;
    }

    @Override
    public BlockPos getLocation() {
        return corePos();
    }

    @Override
    public boolean isLocationEqual(BlockPos p) {
        return corePos().equals(p);
    }

    @Override
    public float getRange() {
        return 16f * rangeScale;
    }

    @Override
    public int getEnhancementLevel() {
        return enhancementLevel;
    }

    @Override
    public boolean isPowerInvalid() {
        return isRemoved();
    }

    @Override
    public float getCurrentPower() {
        if (level == null) {
            return -1f;
        }
        if (level.isClientSide) {
            return -2f;
        }
        if (!isCore()) {
            if (level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe && coreBe != this) {
                return coreBe.getCurrentPower();
            }
            return -1f;
        }
        return power;
    }

    @Override
    public boolean consumePower(float required) {
        if (level == null || level.isClientSide) {
            return false;
        }
        if (!isCore()) {
            return level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe
                    && coreBe != this && coreBe.consumePower(required);
        }
        if (power >= required) {
            power -= required;
            setChanged();
            sync();
            return true;
        }
        return false;
    }

    // ---- GUI readouts (read synced fields directly; side-agnostic) ----
    public float clientPower() {
        if (level != null && !isCore()
                && level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe && coreBe != this) {
            return coreBe.power;
        }
        return power;
    }

    public float clientMaxPower() {
        if (level != null && !isCore()
                && level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe && coreBe != this) {
            return coreBe.maxPower * coreBe.powerScale;
        }
        return maxPower * powerScale;
    }

    // ---- lifecycle / registry ----
    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server) {
            AltarPowerManager.get(server).unregister(this);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel server) {
            AltarPowerManager.get(server).unregister(this);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && isCore()) {
            AltarPowerManager.get(server).register(this);
        }
    }

    // ---- NBT ----
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (core != null) {
            tag.putInt("CoreX", core.getX());
            tag.putInt("CoreY", core.getY());
            tag.putInt("CoreZ", core.getZ());
        }
        tag.putFloat("Power", power);
        tag.putFloat("MaxPower", maxPower);
        tag.putInt("PowerScale", powerScale);
        tag.putInt("RechargeScale", rechargeScale);
        tag.putInt("RangeScale", rangeScale);
        tag.putInt("EnhancementLevel", enhancementLevel);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        core = tag.contains("CoreX")
                ? new BlockPos(tag.getInt("CoreX"), tag.getInt("CoreY"), tag.getInt("CoreZ"))
                : null;
        power = tag.getFloat("Power");
        maxPower = tag.getFloat("MaxPower");
        powerScale = tag.contains("PowerScale") ? tag.getInt("PowerScale") : 1;
        rechargeScale = tag.contains("RechargeScale") ? tag.getInt("RechargeScale") : 1;
        rangeScale = tag.contains("RangeScale") ? tag.getInt("RangeScale") : 1;
        enhancementLevel = tag.getInt("EnhancementLevel");
    }

    // ---- sync (ClientboundBlockEntityDataPacket) ----
    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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

    public void clientTick(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide) return;
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> doClientParticleTick(level, pos));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private void doClientParticleTick(Level level, BlockPos pos) {
        com.vel5id.hexerei.power.TaintLevel tl =
                com.vel5id.hexerei.client.ClientTaintCache.getLevel(new net.minecraft.world.level.ChunkPos(pos));
        if (tl == com.vel5id.hexerei.power.TaintLevel.NONE) return;
        // Particle emission is wired in Task 5 after HexereiParticles is registered.
        // This method is the hook; leave body empty until then.
    }
}
