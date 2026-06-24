package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HexereiParticles {
    private HexereiParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HexereiMod.MODID);

    public static final RegistryObject<SimpleParticleType> WISP_LOW    = PARTICLES.register("wisp_low",    () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WISP_MEDIUM = PARTICLES.register("wisp_medium", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WISP_HIGH   = PARTICLES.register("wisp_high",   () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> ASH         = PARTICLES.register("ash",         () -> new SimpleParticleType(false));

    // Cauldron boiling: bubbles (tinted by brew color) + steam (visible at distance -> overrideLimiter true).
    public static final RegistryObject<SimpleParticleType> CAULDRON_BUBBLE = PARTICLES.register("cauldron_bubble", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> CAULDRON_STEAM  = PARTICLES.register("cauldron_steam",  () -> new SimpleParticleType(true));
}
