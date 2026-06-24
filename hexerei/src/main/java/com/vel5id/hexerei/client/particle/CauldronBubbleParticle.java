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

/** A short-lived bubble rising from a boiling brew, tinted by the brew color passed as the velocity RGB. */
@OnlyIn(Dist.CLIENT)
public class CauldronBubbleParticle extends TextureSheetParticle {

    CauldronBubbleParticle(ClientLevel level, double x, double y, double z,
                           double r, double g, double b, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        pickSprite(sprites);
        lifetime = 12 + random.nextInt(10);
        hasPhysics = false;
        gravity = -0.02f;            // rises
        friction = 0.92f;
        quadSize = 0.06f + random.nextFloat() * 0.05f;
        alpha = 0.85f;
        // RGB came through the velocity args; fall back to neutral if all zero (plain steam path)
        this.rCol = (float) r;
        this.gCol = (float) g;
        this.bCol = (float) b;
        this.yd = 0.02 + random.nextDouble() * 0.02;
    }

    @Override
    public void tick() {
        super.tick();
        if (age > lifetime - 4) {
            alpha = Math.max(0f, alpha - 0.18f);
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
                double x, double y, double z, double r, double g, double b) {
            return new CauldronBubbleParticle(level, x, y, z, r, g, b, sprites);
        }
    }
}
