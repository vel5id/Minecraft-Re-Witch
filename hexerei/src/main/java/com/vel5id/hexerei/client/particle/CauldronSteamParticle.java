package com.vel5id.hexerei.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** A slow, long-lived pale steam wisp rising out of a boiling cauldron. White; uses real velocity. */
@OnlyIn(Dist.CLIENT)
public class CauldronSteamParticle extends TextureSheetParticle {

    CauldronSteamParticle(ClientLevel level, double x, double y, double z,
                          double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        pickSprite(sprites);
        lifetime = 40 + random.nextInt(24);
        hasPhysics = false;
        friction = 0.96f;
        gravity = -0.005f;
        quadSize = 0.10f + random.nextFloat() * 0.06f;
        alpha = 0.45f;
        this.rCol = this.gCol = this.bCol = 0.92f;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
    }

    @Override
    public void tick() {
        super.tick();
        quadSize += 0.004f;          // slight expansion as it rises
        if (age > lifetime - 12) {
            alpha = Math.max(0f, alpha - 0.035f);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet s) { this.sprites = s; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            return new CauldronSteamParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
