package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HexereiParticles {
    private HexereiParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, HexereiMod.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WISP_LOW    = PARTICLES.register("wisp_low",    () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WISP_MEDIUM = PARTICLES.register("wisp_medium", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WISP_HIGH   = PARTICLES.register("wisp_high",   () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ASH         = PARTICLES.register("ash",         () -> new SimpleParticleType(false));

    // Cauldron boiling: bubbles (tinted by brew color) + steam (visible at distance -> overrideLimiter true).
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CAULDRON_BUBBLE = PARTICLES.register("cauldron_bubble", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CAULDRON_STEAM  = PARTICLES.register("cauldron_steam",  () -> new SimpleParticleType(true));
}
