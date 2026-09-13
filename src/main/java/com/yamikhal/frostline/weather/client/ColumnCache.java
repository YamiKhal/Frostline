package com.yamikhal.frostline.weather.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Per-column precipitation, surface height and light around the camera.
 *
 * Vanilla's weather renderer looks up the biome, heightmap and light of every column on
 * every frame (and allocates a RandomSource per column). These change slowly, so they are
 * read here once every few ticks or when the camera changes column, and each frame only
 * does arithmetic.
 */
final class ColumnCache {

    static final int RADIUS = 16;
    private static final int SIZE = RADIUS * 2 + 1;
    private static final int REFRESH_TICKS = 5;

    static final byte NONE = 0;
    static final byte RAIN = 1;
    static final byte SNOW = 2;

    final byte[] precipitation = new byte[SIZE * SIZE];
    final int[] height = new int[SIZE * SIZE];
    final int[] light = new int[SIZE * SIZE];
    final int[] snowLight = new int[SIZE * SIZE];
    int centerX;
    int centerY;
    int centerZ;
    int snowColumns;
    int rainColumns;
    boolean valid;

    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    void invalidate() {
        valid = false;
    }

    void update(ClientLevel level, BlockPos eye, int tick) {
        boolean moved = !valid || eye.getX() != centerX || eye.getZ() != centerZ || Math.abs(eye.getY() - centerY) > 3;
        if (!moved && tick % REFRESH_TICKS != 0) {
            return;
        }
        centerX = eye.getX();
        centerY = eye.getY();
        centerZ = eye.getZ();
        snowColumns = 0;
        rainColumns = 0;
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int i = (dz + RADIUS) * SIZE + dx + RADIUS;
                int x = centerX + dx;
                int z = centerZ + dz;
                pos.set(x, centerY, z);
                Biome biome = level.getBiome(pos).value();
                if (!biome.hasPrecipitation()) {
                    precipitation[i] = NONE;
                    continue;
                }
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                height[i] = h;
                pos.set(x, Math.max(h, centerY), z);
                Biome.Precipitation p = biome.getPrecipitationAt(pos);
                precipitation[i] = p == Biome.Precipitation.SNOW ? SNOW : p == Biome.Precipitation.RAIN ? RAIN : NONE;
                int packed = LevelRenderer.getLightColor(level, pos);
                light[i] = packed;
                // Vanilla brightens snow toward full light so it reads white at night.
                int sky = (((packed >> 16) & 0xFFFF) * 3 + 240) / 4;
                int block = ((packed & 0xFFFF) * 3 + 240) / 4;
                snowLight[i] = block | sky << 16;
                if (precipitation[i] == SNOW) {
                    snowColumns++;
                } else if (precipitation[i] == RAIN) {
                    rainColumns++;
                }
            }
        }
        valid = true;
    }

    /** Index of a column, or -1 outside the cache. */
    int index(int x, int z) {
        int dx = x - centerX;
        int dz = z - centerZ;
        if (dx < -RADIUS || dx > RADIUS || dz < -RADIUS || dz > RADIUS) {
            return -1;
        }
        return (dz + RADIUS) * SIZE + dx + RADIUS;
    }

    boolean snowAt(int x, int z) {
        int i = valid ? index(x, z) : -1;
        return i >= 0 && precipitation[i] == SNOW;
    }
}
