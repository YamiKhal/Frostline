package com.yamikhal.frostline.weather.client;

import com.yamikhal.frostline.weather.FrostlineWeather;
import com.yamikhal.frostline.weather.WeatherConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Snow particles from the world around the player, at any weather:
 *
 *   sifting   grains falling from the underside of #frostline:sifting_snow blocks and of
 *             leaves carrying snow, when there is air below. More in storms.
 *   drifting  grains blown off exposed snow by the wind. Scales with the storm, so mostly
 *             in storms and a little in #frostline:windswept biomes.
 *
 * Like vanilla's animateTick, it samples random blocks near the player each tick instead of
 * scanning an area, so the cost is samplesPerTick block reads whatever is around.
 */
final class AmbientSnow {

    private static final int HORIZONTAL = 16;
    private static final int VERTICAL = 12;

    private AmbientSnow() {
    }

    static void tick(Minecraft mc, ClientLevel level, BlockPos eye, float storm, double windX, double windZ) {
        WeatherConfig.Client cfg = WeatherConfig.CLIENT;
        ParticleStatus status = mc.options.particles().get();
        int samples = cfg.particleSamples.get();
        if (status == ParticleStatus.MINIMAL || samples <= 0) {
            return;
        }
        if (status == ParticleStatus.DECREASED) {
            samples /= 2;
        }
        float strength = Mth.clamp(storm * WeatherClient.gust(), 0, 1);
        float sift = cfg.sifting.get().floatValue() * 0.5f * (1 + strength);
        float drift = cfg.drifting.get().floatValue() * 0.4f * strength;
        if (sift <= 0 && drift <= 0) {
            return;
        }
        RandomSource random = level.random;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int n = 0; n < samples; n++) {
            int x = eye.getX() + random.nextInt(HORIZONTAL * 2 + 1) - HORIZONTAL;
            int y = eye.getY() + random.nextInt(VERTICAL * 2 + 1) - VERTICAL;
            int z = eye.getZ() + random.nextInt(HORIZONTAL * 2 + 1) - HORIZONTAL;
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.isAir()) {
                continue;
            }
            if (sift > 0 && sifts(level, pos, state)) {
                if (random.nextFloat() < sift && level.getBlockState(pos.set(x, y - 1, z)).isAir()) {
                    level.addParticle(FrostlineWeather.SNOW_DRIFT.get(),
                            x + random.nextDouble(), y - 0.05, z + random.nextDouble(),
                            windX * 0.1, -0.02, windZ * 0.1);
                }
            } else if (drift > 0 && isSnow(state)) {
                if (random.nextFloat() < drift
                        && level.getBlockState(pos.set(x, y + 1, z)).isAir()
                        && level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) <= y + 1) {
                    double top = state.getBlock() instanceof SnowLayerBlock
                            ? state.getValue(SnowLayerBlock.LAYERS) / 8.0 : 1.0;
                    level.addParticle(FrostlineWeather.SNOW_DRIFT.get(),
                            x + random.nextDouble(), y + top + 0.05, z + random.nextDouble(),
                            windX * 0.8, 0.03 + random.nextDouble() * 0.05, windZ * 0.8);
                }
            }
        }
    }

    private static boolean sifts(ClientLevel level, BlockPos.MutableBlockPos pos, BlockState state) {
        if (state.is(FrostlineWeather.SIFTING_SNOW)) {
            return true;
        }
        if (!state.is(BlockTags.LEAVES)) {
            return false;
        }
        boolean snowAbove = isSnow(level.getBlockState(pos.move(0, 1, 0)));
        pos.move(0, -1, 0);
        return snowAbove;
    }

    private static boolean isSnow(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
    }
}
