package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.blockentity.AltarBlockEntity;
import com.vel5id.hexerei.blockentity.CauldronBlockEntity;
import com.vel5id.hexerei.blockentity.RitualSigilBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HexereiBlockEntities {
    private HexereiBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HexereiMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AltarBlockEntity>> ALTAR =
            BLOCK_ENTITIES.register("altar",
                    () -> BlockEntityType.Builder.of(AltarBlockEntity::new, HexereiBlocks.ALTAR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CauldronBlockEntity>> CAULDRON =
            BLOCK_ENTITIES.register("cauldron",
                    () -> BlockEntityType.Builder.of(CauldronBlockEntity::new, HexereiBlocks.CAULDRON.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RitualSigilBlockEntity>> RITUAL_SIGIL =
            BLOCK_ENTITIES.register("ritual_sigil",
                    () -> BlockEntityType.Builder.of(RitualSigilBlockEntity::new, HexereiBlocks.RITUAL_SIGIL.get()).build(null));
}
