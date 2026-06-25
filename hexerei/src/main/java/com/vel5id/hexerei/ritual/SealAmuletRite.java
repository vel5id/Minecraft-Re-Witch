package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.item.AmuletItem;
import com.vel5id.hexerei.item.SealedAmulet;
import com.vel5id.hexerei.registry.HexereiItems;
import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Correspondence;
import com.vel5id.hexerei.soul.Disposition;
import com.vel5id.hexerei.soul.Mark;
import com.vel5id.hexerei.soul.MarkScope;
import com.vel5id.hexerei.soul.SealRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The "запечатать" verb (Модель §7): seals a fresh spirit of {@code domain} into an amulet. Builds a
 * {@link Bond} (full seal, a domain Mark, zeroed disposition) and spawns a {@code hexerei:amulet}
 * carrying it at the circle centre. One class, domain in the constructor (the SpawnItemRite pattern) —
 * the three domain recipes are three instances, their sacrifice reagent diegetically the spirit's domain.
 *
 * <p>The {@link Rite} contract carries no player handle, so the amulet drops un-owned (whoever pouches
 * it later binds it) — the same deferred-owner caveat as {@link BoundBeastRite}.
 */
public final class SealAmuletRite implements Rite {

    private final Correspondence domain;

    public SealAmuletRite(Correspondence domain) {
        this.domain = domain;
    }

    @Override
    public void perform(ServerLevel level, BlockPos center) {
        long now = level.getGameTime();
        Bond bond = new Bond(
                UUID.randomUUID(),
                new ResourceLocation("hexerei", domain.key() + "_warden"),
                domain,
                Disposition.EMPTY,
                new SealRef(SealedAmulet.FULL_INTEGRITY),
                List.of(new Mark(MarkScope.DOMAIN, domain, SealedAmulet.SEAL_MARK_SEVERITY)),
                List.of(),
                now, now);

        ItemStack stack = new ItemStack(HexereiItems.AMULET.get());
        AmuletItem.writeBond(stack, bond);

        ItemEntity ie = new ItemEntity(level,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5, stack);
        ie.setDeltaMovement(Vec3.ZERO);
        ie.setDefaultPickUpDelay();
        level.addFreshEntity(ie);

        level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.2f);
        level.sendParticles(ParticleTypes.END_ROD,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                24, 0.1, 0.6, 0.1, 0.05);

        // Sealing a spirit disturbs its domain (Article III): power source and danger source are one act.
        Rites.addRitualTaint(level, new ChunkPos(center), domain, SealedAmulet.SEAL_DISTURBANCE);
    }
}
