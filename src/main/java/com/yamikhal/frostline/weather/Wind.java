package com.yamikhal.frostline.weather;

/**
 * Wind as a pure function of game time, so every player on a server sees the same wind
 * without any packets: a direction that wanders over about half a day and gusts that come
 * and go over a few seconds.
 */
public final class Wind {

    private Wind() {
    }

    /** Wind heading in radians. */
    public static double direction(double gameTime) {
        return Math.PI * 2 * (0.6 + 1.5 * noise(gameTime / 12000.0, 11));
    }

    /** Gust factor, roughly 0.35..1.65 around 1. */
    public static double gust(double gameTime) {
        return 1 + 0.45 * noise(gameTime / 70.0, 23) + 0.2 * noise(gameTime / 19.0, 37);
    }

    /** Smooth value noise in -1..1. */
    static double noise(double t, int seed) {
        long i = (long) Math.floor(t);
        double f = t - i;
        f = f * f * (3 - 2 * f);
        return hash(i, seed) + (hash(i + 1, seed) - hash(i, seed)) * f;
    }

    private static double hash(long i, int seed) {
        long h = i * 0x9E3779B97F4A7C15L + seed * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }
}
