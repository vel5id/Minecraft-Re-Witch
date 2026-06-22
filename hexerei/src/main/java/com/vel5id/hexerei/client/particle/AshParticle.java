package com.vel5id.hexerei.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AshParticle extends TextureSheetParticle {

    AshParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        pickSprite(sprites);
        lifetime = 20 + random.nextInt(15);
        hasPhysics = false;
        gravity = 0.03f;
        quadSize = 0.06f + random.nextFloat() * 0.04f;
        alpha = 0.5f;
        rCol = 0.15f;
        gCol = 0.1f;
        bCol = 0.1f;
        xd = (random.nextDouble() - 0.5) * 0.02;
        zd = (random.nextDouble() - 0.5) * 0.02;
    }

    @Override
    public void tick() {
        super.tick();
        if (age > lifetime - 6) alpha = Math.max(0f, alpha - 0.08f);
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
            return new AshParticle(level, x, y, z, sprites);
        }
    }
}
