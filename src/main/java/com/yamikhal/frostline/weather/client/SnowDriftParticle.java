package com.yamikhal.frostline.weather.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A small snow grain that falls slowly and is carried by the wind. Uses vanilla's snowflake sprites. */
final class SnowDriftParticle extends TextureSheetParticle {

    private SnowDriftParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = vx;
        yd = vy;
        zd = vz;
        gravity = 0.08f;
        friction = 0.92f;
        quadSize = 0.06f * (0.7f + random.nextFloat() * 0.6f);
        lifetime = 30 + random.nextInt(40);
        setColor(0.93f, 0.96f, 1f);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            xd += (WeatherClient.windX(level, 0) - xd) * 0.06;
            zd += (WeatherClient.windZ(level, 0) - zd) * 0.06;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new SnowDriftParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
