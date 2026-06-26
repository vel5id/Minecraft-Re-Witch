package com.vel5id.hexerei.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.soul.Bond;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Custom data components — the 1.21 successor to the item NBT this mod used on 1.20.1. Each component
 * carries the exact value the old {@code getOrCreateTag()} key did, so item behaviour is unchanged; only
 * the storage substrate moves from a raw {@code CompoundTag} to a typed, codec-backed component.
 *
 * <p>Read/write on a stack with {@code stack.get(COMPONENT.get())}, {@code stack.set(COMPONENT.get(), v)},
 * {@code stack.has(COMPONENT.get())} (note the {@code .get()} — the field is the deferred holder).
 */
public final class HexereiDataComponents {
    private HexereiDataComponents() {}

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(HexereiMod.MODID);

    /** AmuletItem: the sealed {@link Bond} (was NBT key "hexerei:sealed_bond"). */
    public static final Supplier<DataComponentType<Bond>> SEALED_BOND =
            DATA_COMPONENTS.registerComponentType("sealed_bond", builder -> builder
                    .persistent(Bond.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(Bond.CODEC)));

    /** TaglockItem: the bound victim's id + name (was NBT keys "hexerei:target_uuid"/"hexerei:target_name"). */
    public static final Supplier<DataComponentType<TaglockTarget>> TAGLOCK_TARGET =
            DATA_COMPONENTS.registerComponentType("taglock_target", builder -> builder
                    .persistent(TaglockTarget.CODEC)
                    .networkSynchronized(TaglockTarget.STREAM_CODEC));

    /** BrewItem: the brew catalog id (was NBT key "BrewId"). */
    public static final Supplier<DataComponentType<String>> BREW_ID =
            DATA_COMPONENTS.registerComponentType("brew_id", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** CharmPouchItem: the held sealed amulets (was an ItemStackHandler under NBT key "hexerei:Items"). */
    public static final Supplier<DataComponentType<ItemContainerContents>> POUCH_CONTENTS =
            DATA_COMPONENTS.registerComponentType("pouch_contents", builder -> builder
                    .persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /** RitualChalkItem: the selected rite id (was NBT key "hexerei:rite"). */
    public static final Supplier<DataComponentType<String>> SELECTED_RITE =
            DATA_COMPONENTS.registerComponentType("selected_rite", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** A taglock's bound victim: their UUID and last-seen name. */
    public record TaglockTarget(UUID id, String name) {
        public static final Codec<TaglockTarget> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(TaglockTarget::id),
                Codec.STRING.fieldOf("name").forGetter(TaglockTarget::name)
        ).apply(inst, TaglockTarget::new));

        public static final StreamCodec<ByteBuf, TaglockTarget> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, TaglockTarget::id,
                ByteBufCodecs.STRING_UTF8, TaglockTarget::name,
                TaglockTarget::new);
    }
}
