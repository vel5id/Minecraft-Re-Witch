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

@OnlyIn(Dist.CLIENT)
public class WispParticle extends TextureSheetParticle {

    WispParticle(ClientLevel level, double x, double y, double z,
                 double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        pickSprite(sprites);
        lifetime = 30 + random.nextInt(20);
        hasPhysics = false;
        friction = 0.94f;
        gravity = -0.015f;
        quadSize = 0.12f + random.nextFloat() * 0.08f;
        alpha = 0.75f;
    }

    @Override
    public void tick() {
        super.tick();
        xd *= 0.96f;
        zd *= 0.96f;
        if (age > lifetime - 8) alpha = Math.max(0f, alpha - 0.09f);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class LowProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public LowProvider(SpriteSet s) { this.sprites = s; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            WispParticle p = new WispParticle(level, x, y, z, vx, vy, vz, sprites);
            p.rCol = 0x6E / 255f;
            p.gCol = 0x14 / 255f;
            p.bCol = 0xBE / 255f;
            return p;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class MediumProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public MediumProvider(SpriteSet s) { this.sprites = s; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            WispParticle p = new WispParticle(level, x, y, z, vx, vy, vz, sprites);
            p.rCol = 0x46 / 255f;
            p.gCol = 0f;
            p.bCol = 0x9B / 255f;
            return p;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class HighProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public HighProvider(SpriteSet s) { this.sprites = s; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            WispParticle p = new WispParticle(level, x, y, z, vx, vy, vz, sprites);
            p.rCol = 0xA0 / 255f;
            p.gCol = 0f;
            p.bCol = 0x4A / 255f;
            return p;
        }
    }
}
