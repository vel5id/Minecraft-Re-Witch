package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.blockentity.AltarBlockEntity;
import com.vel5id.hexerei.blockentity.CauldronBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HexereiBlockEntities {
    private HexereiBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HexereiMod.MODID);

    public static final RegistryObject<BlockEntityType<AltarBlockEntity>> ALTAR =
            BLOCK_ENTITIES.register("altar",
                    () -> BlockEntityType.Builder.of(AltarBlockEntity::new, HexereiBlocks.ALTAR.get()).build(null));

    public static final RegistryObject<BlockEntityType<CauldronBlockEntity>> CAULDRON =
            BLOCK_ENTITIES.register("cauldron",
                    () -> BlockEntityType.Builder.of(CauldronBlockEntity::new, HexereiBlocks.CAULDRON.get()).build(null));
}
