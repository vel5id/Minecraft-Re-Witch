package com.vel5id.hexerei.blockentity;

import com.vel5id.hexerei.block.AltarBlock;
import com.vel5id.hexerei.block.AltarFormation;
import com.vel5id.hexerei.item.ArtefactDef;
import com.vel5id.hexerei.item.ArtefactItem;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.AltarPowerTable;
import com.vel5id.hexerei.power.IPowerSource;
import com.vel5id.hexerei.power.PowerSource;
import com.vel5id.hexerei.registry.HexereiBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.ItemStack;

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
    private boolean hungering = false; // a rite awakened the altar into a life-draining Hungering Altar
    // Per-domain offering satiation (core-only): bulk-feeding one domain yields less; decays over time.
    private final java.util.EnumMap<com.vel5id.hexerei.soul.Correspondence, Float> satiation =
            new java.util.EnumMap<>(com.vel5id.hexerei.soul.Correspondence.class);
    // The placed artefact (one slot per altar; core-only) and the multipliers derived from it.
    private ItemStack artefact = ItemStack.EMPTY;
    private float taintMul = 1f;
    private float effectMul = 1f;
    private long ticks = 0;
    private long lastPowerUpdate = 0;

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(HexereiBlockEntities.ALTAR.get(), pos, state);
    }

    public boolean isCore() {
        return core != null && core.equals(worldPosition);
    }

    public BlockPos corePos() {
        return core != null ? core : worldPosition;
    }

    /** The {@link AltarBlockEntity} that owns the shared artefact slot — this BE if it is the core, else the core. */
    @Nullable
    public AltarBlockEntity coreBe() {
        if (isCore()) {
            return this;
        }
        return level != null && level.getBlockEntity(corePos()) instanceof AltarBlockEntity be ? be : null;
    }

    // ---- artefact slot (one per altar; lives on the core BE) ----

    /** The placed artefact stack (on the core), or {@link ItemStack#EMPTY}. Routes to the core. */
    public ItemStack getArtefact() {
        AltarBlockEntity c = coreBe();
        return c == null ? ItemStack.EMPTY : c.artefact;
    }

    /**
     * Stores {@code stack} (count 1) as the altar's artefact and returns the previously held one. Routes to
     * the core, recomputes the multipliers, and persists/syncs. {@code stack} may be {@link ItemStack#EMPTY}
     * to clear the slot.
     */
    public ItemStack setArtefact(ItemStack stack) {
        AltarBlockEntity c = coreBe();
        if (c == null) {
            return ItemStack.EMPTY;
        }
        ItemStack prev = c.artefact;
        c.artefact = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        c.applyArtefact();
        return prev;
    }

    /** Clears the artefact slot, returning the previously held stack. Routes to the core. */
    public ItemStack clearArtefact() {
        return setArtefact(ItemStack.EMPTY);
    }

    /** The current taint multiplier from the placed artefact (1.0 with none). Routes to the core. */
    public float taintMultiplier() {
        AltarBlockEntity c = coreBe();
        return c == null ? 1f : c.taintMul;
    }

    /** The current effect multiplier from the placed artefact (1.0 with none). Routes to the core. */
    public float effectMultiplier() {
        AltarBlockEntity c = coreBe();
        return c == null ? 1f : c.effectMul;
    }

    /**
     * Recomputes the derived multipliers and {@code enhancementLevel} from the placed artefact — the single
     * source of truth, called after any artefact change and on {@link #onLoad}. Core-only; persists and syncs.
     */
    public void applyArtefact() {
        if (!isCore()) {
            return;
        }
        ArtefactDef d = ArtefactItem.defOf(artefact);
        this.taintMul = d == null ? 1f : d.taintMul();
        this.effectMul = d == null ? 1f : d.effectMul();
        this.enhancementLevel = d == null ? 0 : d.enhancement();
        setChanged();
        sync();
    }

    // ---- ticking ----
    public static void serverTick(Level level, BlockPos pos, BlockState state, AltarBlockEntity be) {
        be.ticks++;
        if (!be.isCore()) {
            return;
        }
        // Article III (WARRANTLY): no passive recharge. The altar is an essence RESERVOIR earned by
        // taking from the world (see EssenceSourcing); maxScaled is only the capacity cap. Power that
        // somehow exceeds the cap is clamped down, but it is never filled for free.
        float maxScaled = be.maxPower * be.powerScale;
        if (be.power > maxScaled && be.ticks % 20L == 0L) {
            be.power = maxScaled;
            be.sync();
        }

        // Hungering Altar (WARRANTLY Статья III): once awakened by a rite, drain the nearest life for
        // essence and curse the surrounding region. Power and danger, the same act.
        if (be.hungering && level instanceof net.minecraft.server.level.ServerLevel hsl) {
            if (be.ticks % com.vel5id.hexerei.ritual.HungeringAltar.DRAIN_INTERVAL == 0L && be.power < maxScaled) {
                com.vel5id.hexerei.soul.Act act = com.vel5id.hexerei.soul.AltarDrain.drainNearest(
                        hsl, pos, com.vel5id.hexerei.ritual.HungeringAltar.DRAIN_RADIUS);
                if (act != null) {
                    be.gainEssence(com.vel5id.hexerei.soul.EssenceSource.essenceFrom(act));
                }
            }
            if (be.ticks % com.vel5id.hexerei.ritual.HungeringAltar.PENALTY_INTERVAL == 0L) {
                float disturbance = hsl.getChunkAt(pos)
                        .getData(com.vel5id.hexerei.soul.HexereiAttachments.CHUNK_SOUL)
                        .totalDisturbance();
                com.vel5id.hexerei.ritual.HungeringAltar.applyRegionalPenalty(hsl, pos, disturbance);
            }
        }

        // Offering satiation eases back toward zero (the altar's hunger for a domain returns).
        if (!be.satiation.isEmpty() && be.ticks % 20L == 0L) {
            be.satiation.replaceAll((d, v) -> com.vel5id.hexerei.soul.Offering.decay(v, 1f));
            be.satiation.values().removeIf(v -> v <= 0f);
        }

        // Sync taint level to blockstate every 40 ticks (core only)
        if (be.ticks % 40 == 0 && be.isCore() && level instanceof net.minecraft.server.level.ServerLevel sl) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(pos);
            com.vel5id.hexerei.power.TaintLevel tl = com.vel5id.hexerei.soul.Disturbance.level(sl, cp);
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
                // De-form must not trap the artefact: pop it to the world and reset the derived multipliers.
                if (!artefact.isEmpty()) {
                    Block.popResource(server, worldPosition, artefact);
                    artefact = ItemStack.EMPTY;
                }
                taintMul = 1f;
                effectMul = 1f;
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
            Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(e.getKey()));
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
        } else if (bs.is(com.vel5id.hexerei.registry.HexereiBlocks.BLOOD_MOSS.get())) {
            e = AltarPowerTable.CROP;            // 4/20 for blood moss (reuses the reserved moss-tier; [UNVERIFIED] balance)
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

    /**
     * Credit essence earned from a take in the world (see {@code EssenceSourcing}). Routes to the
     * multiblock core and is clamped to the reservoir capacity — essence is earned, never granted free.
     */
    public void gainEssence(float amount) {
        if (level == null || level.isClientSide || amount <= 0f) {
            return;
        }
        if (!isCore()) {
            if (level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe && coreBe != this) {
                coreBe.gainEssence(amount);
            }
            return;
        }
        power = Math.min(power + amount, maxPower * powerScale);
        setChanged();
        sync();
    }

    /** Awaken/quiet the Hungering Altar (a rite). Routes to the multiblock core. */
    public void setHungering(boolean value) {
        if (!isCore()) {
            if (level != null && level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe && coreBe != this) {
                coreBe.setHungering(value);
            }
            return;
        }
        if (hungering != value) {
            hungering = value;
            setChanged();
            sync();
        }
    }

    public boolean isHungering() {
        if (!isCore() && level != null
                && level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe && coreBe != this) {
            return coreBe.hungering;
        }
        return hungering;
    }

    /**
     * Offer a reagent's spirit to the altar (a deliberate sacrifice): credits essence with per-domain
     * diminishing returns and raises that domain's satiation. Routes to the core; returns the essence
     * actually gained (0 off-server / no core).
     */
    public float offer(com.vel5id.hexerei.soul.ReagentDescriptor reagent) {
        if (level == null || level.isClientSide) {
            return 0f;
        }
        if (!isCore()) {
            return level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe && coreBe != this
                    ? coreBe.offer(reagent) : 0f;
        }
        float sat = satiation.getOrDefault(reagent.domain(), 0f);
        float gained = com.vel5id.hexerei.soul.Offering.essence(reagent.magnitude(), sat);
        gainEssence(gained);
        satiation.put(reagent.domain(), com.vel5id.hexerei.soul.Offering.satiationAfter(sat, reagent.magnitude()));
        setChanged();
        sync();
        return gained;
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
            applyArtefact();   // recompute the derived multipliers from the persisted artefact
        }
    }

    // ---- NBT ----
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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
        if (!artefact.isEmpty()) {
            tag.put("Artefact", artefact.save(registries));
        }
        // Derived from the artefact but persisted so a chunk-load before onLoad's recompute is still correct.
        tag.putFloat("TaintMul", taintMul);
        tag.putFloat("EffectMul", effectMul);
        tag.putBoolean("Hungering", hungering);
        if (!satiation.isEmpty()) {
            CompoundTag sat = new CompoundTag();
            satiation.forEach((d, v) -> sat.putFloat(d.key(), v));
            tag.put("Satiation", sat);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        core = tag.contains("CoreX")
                ? new BlockPos(tag.getInt("CoreX"), tag.getInt("CoreY"), tag.getInt("CoreZ"))
                : null;
        power = tag.getFloat("Power");
        maxPower = tag.getFloat("MaxPower");
        powerScale = tag.contains("PowerScale") ? tag.getInt("PowerScale") : 1;
        rechargeScale = tag.contains("RechargeScale") ? tag.getInt("RechargeScale") : 1;
        rangeScale = tag.contains("RangeScale") ? tag.getInt("RangeScale") : 1;
        enhancementLevel = tag.getInt("EnhancementLevel");
        artefact = tag.contains("Artefact") ? ItemStack.parseOptional(registries, tag.getCompound("Artefact")) : ItemStack.EMPTY;
        taintMul = tag.contains("TaintMul") ? tag.getFloat("TaintMul") : 1f;
        effectMul = tag.contains("EffectMul") ? tag.getFloat("EffectMul") : 1f;
        hungering = tag.getBoolean("Hungering");
        satiation.clear();
        if (tag.contains("Satiation")) {
            CompoundTag sat = tag.getCompound("Satiation");
            for (String k : sat.getAllKeys()) {
                com.vel5id.hexerei.soul.Correspondence d = com.vel5id.hexerei.soul.Correspondence.byKey(k);
                if (d != null) satiation.put(d, sat.getFloat(k));
            }
        }
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    public void clientTick(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide) return;
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            doClientParticleTick(level, pos);
        }
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private void doClientParticleTick(Level level, BlockPos pos) {
        com.vel5id.hexerei.power.TaintLevel tl =
                com.vel5id.hexerei.client.ClientTaintCache.getLevel(new net.minecraft.world.level.ChunkPos(pos));
        if (tl == com.vel5id.hexerei.power.TaintLevel.NONE) return;

        long gt = level.getGameTime();
        int period = switch (tl) {
            case LOW    -> 20;
            case MEDIUM -> 20;
            case HIGH   -> 10;
            default     -> 0;
        };
        if (period == 0 || gt % period != 0) return;

        int wisps = switch (tl) {
            case LOW    -> 1;
            case MEDIUM -> 2;
            case HIGH   -> 3;
            default     -> 0;
        };
        int ashes = tl == com.vel5id.hexerei.power.TaintLevel.HIGH ? 1 : 0;

        net.minecraft.core.particles.SimpleParticleType wispType = switch (tl) {
            case LOW    -> com.vel5id.hexerei.registry.HexereiParticles.WISP_LOW.get();
            case MEDIUM -> com.vel5id.hexerei.registry.HexereiParticles.WISP_MEDIUM.get();
            case HIGH   -> com.vel5id.hexerei.registry.HexereiParticles.WISP_HIGH.get();
            default     -> null;
        };
        if (wispType != null) {
            for (int i = 0; i < wisps; i++) {
                double px = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.8;
                double py = pos.getY() + 1.1;
                double pz = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.8;
                double vy = 0.02 + level.random.nextDouble() * 0.02;
                level.addParticle(wispType, px, py, pz, 0, vy, 0);
            }
        }

        for (int i = 0; i < ashes; i++) {
            double px = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 1.4;
            double py = pos.getY() + 1.6;
            double pz = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 1.4;
            level.addParticle(com.vel5id.hexerei.registry.HexereiParticles.ASH.get(), px, py, pz, 0, 0, 0);
        }
    }
}
