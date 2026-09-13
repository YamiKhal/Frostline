package com.yamikhal.frostline.weather;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.TickEvent;

/**
 * Decides when it storms. A storm is plain vanilla rain on the overworld (which every
 * dimension sharing its weather follows), so snow accumulation, sky darkening, mob
 * behaviour and the client's rain level all stay vanilla. Frostline only replaces the
 * random rain cycle with the windowed schedule in WeatherConfig.Common.
 *
 * Every second it re-arms the vanilla timers with a short buffer, so vanilla never flips the
 * weather on its own, and if Frostline is removed the world returns to the vanilla cycle
 * within five minutes. Changes made by someone else (/weather, sleeping) are noticed by
 * comparing the vanilla flag with the saved state and adopted.
 */
public final class StormScheduler {

    static final int DAY = 24000;
    private static final int CHECK_INTERVAL = 20;
    private static final int TIMER_BUFFER = 6000;
    /** Never start a rolled storm this close to its window's end. */
    private static final int MIN_STORM = 1200;

    private StormScheduler() {
    }

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }
        update(level);
    }

    static StormState state(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StormState::load, StormState::new, StormState.NAME);
    }

    static void update(ServerLevel level) {
        if (!(level.getLevelData() instanceof ServerLevelData data)) {
            return;
        }
        StormState s = state(level);
        if (!WeatherConfig.COMMON.enabled.get()) {
            if (s.controlling) {
                // Hand the weather back to vanilla with a normal clear spell.
                level.setWeatherParameters(ServerLevel.RAIN_DELAY.sample(level.random), 0, false, false);
                s.controlling = false;
                s.storm = false;
                s.pendingStart = -1;
                s.setDirty();
            }
            return;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
            return;
        }
        long t = level.getDayTime();
        if (s.controlling) {
            adoptExternalChange(level, data, s, t);
        }
        advance(level, s, t);
        apply(level, data, s);
        s.controlling = true;
        s.setDirty();
    }

    private static void adoptExternalChange(ServerLevel level, ServerLevelData data, StormState s, long t) {
        boolean raining = data.isRaining();
        if (raining && !s.storm) {
            // /weather rain or thunder: a storm for the requested duration.
            startStorm(s, t + Math.max(data.getRainTime(), MIN_STORM), true);
        } else if (!raining && s.storm) {
            // /weather clear sets a clear time; sleeping resets rain without one.
            boolean command = data.getClearWeatherTime() > 0;
            if (command) {
                endStorm(s, t, false);
            } else if (WeatherConfig.COMMON.sleepEndsStorm.get()) {
                endStorm(s, t, !s.manual);
            }
        }
    }

    private static void advance(ServerLevel level, StormState s, long t) {
        WeatherConfig.Common c = WeatherConfig.COMMON;
        long windowStart = c.windowStart.get();
        long length = windowLength();
        long cycle = Math.floorDiv(t - windowStart, DAY);
        long open = cycle * DAY + windowStart;
        long close = open + length;

        if (t < s.lastTime - MIN_STORM) {
            // Time was set backwards: forget the future.
            if (s.storm) {
                endStorm(s, t, false);
            }
            s.pendingStart = -1;
            s.decidedCycle = Math.min(s.decidedCycle, cycle - 1);
            s.cooldownUntilCycle = Math.min(s.cooldownUntilCycle, cycle);
        }
        s.lastTime = t;

        if (s.storm) {
            while (t >= s.stormEnd) {
                if (!s.manual && s.stormDays < c.maxStormDays.get()
                        && level.random.nextDouble() < c.continueChance.get()) {
                    s.stormEnd += DAY;
                    s.stormDays++;
                } else {
                    endStorm(s, t, !s.manual);
                    break;
                }
            }
            return;
        }

        if (s.pendingStart >= 0) {
            if (t >= s.pendingClose) {
                s.pendingStart = -1;
            } else if (t >= s.pendingStart) {
                startStorm(s, s.pendingClose, false);
            }
            return;
        }

        if (t < close && cycle > s.decidedCycle) {
            s.decidedCycle = cycle;
            long day = Math.floorDiv(t, DAY);
            if (day >= c.firstStormDay.get() && cycle >= s.cooldownUntilCycle
                    && level.random.nextDouble() >= c.skipChance.get()) {
                int maxDelay = c.maxStartDelay.get();
                long start = open + (maxDelay > 0 ? level.random.nextInt(maxDelay + 1) : 0);
                start = Math.max(open, Math.min(start, close - MIN_STORM));
                if (t >= start) {
                    startStorm(s, close, false);
                } else {
                    s.pendingStart = start;
                    s.pendingClose = close;
                }
            }
        }
    }

    private static void apply(ServerLevel level, ServerLevelData data, StormState s) {
        if (s.storm) {
            level.setWeatherParameters(0, TIMER_BUFFER, true, data.isThundering());
        } else {
            level.setWeatherParameters(TIMER_BUFFER, 0, false, false);
        }
    }

    static void startStorm(StormState s, long end, boolean manual) {
        s.storm = true;
        s.manual = manual;
        s.stormDays = 1;
        s.stormEnd = end;
        s.pendingStart = -1;
    }

    static void endStorm(StormState s, long t, boolean cooldown) {
        s.storm = false;
        s.pendingStart = -1;
        long windowStart = WeatherConfig.COMMON.windowStart.get();
        long cycle = Math.floorDiv(t - windowStart, DAY);
        // A storm that ends inside a window never restarts in that same window.
        s.decidedCycle = Math.max(s.decidedCycle, cycle);
        if (cooldown) {
            long endCycle = Math.floorDiv(Math.min(s.stormEnd, t) - 1 - windowStart, DAY);
            s.cooldownUntilCycle = endCycle + 1 + WeatherConfig.COMMON.cooldownDays.get();
        }
    }

    static long windowLength() {
        long len = Math.floorMod(WeatherConfig.COMMON.windowEnd.get() - WeatherConfig.COMMON.windowStart.get(), DAY);
        return len == 0 ? DAY : len;
    }

    /** Pushes a state change made by a command to vanilla immediately. */
    static void applyNow(ServerLevel level) {
        if (level.getLevelData() instanceof ServerLevelData data) {
            StormState s = state(level);
            s.lastTime = level.getDayTime();
            apply(level, data, s);
            s.controlling = true;
            s.setDirty();
        }
    }
}
