package com.yamikhal.frostline.compat.railways;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.vodmordia.railwaysuntold.datapack.EventTrigger;
import com.vodmordia.railwaysuntold.datapack.TriggerContext;
import com.yamikhal.frostline.ClimateProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Railways Untold event trigger "frostline:climate": the head stands where a climate
 * value is inside [min, max].
 *
 *   { "type": "frostline:climate", "parameter": "continentalness", "min": 0.25, "backstop": 0.28 }
 *
 * See {@link ClimateProbe} for parameter, axis and jitter. "backstop" marks the event as
 * a line terminus: past that value {@link TerminusBackstop} forces the event instead of
 * waiting for the random roll.
 *
 * EventTrigger is a sealed interface in Railways Untold 1.2.2, so this cannot implement
 * it. Each parsed trigger is registered under a reserved negative key and carried inside
 * an EventTrigger.GameTime(key): no real game time is below KEY_CEILING, and
 * GameTimeTriggerMixin sends test() for those keys here.
 */
public final class ClimateTrigger {

    public static final String TYPE = "frostline:climate";
    /** Carrier keys live below this; real GameTime triggers use tick counts >= 0. */
    public static final long KEY_CEILING = Long.MIN_VALUE / 2;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicLong NEXT_KEY = new AtomicLong(Long.MIN_VALUE);
    private static final Map<Long, ClimateTrigger> BY_KEY = new ConcurrentHashMap<>();

    private final ClimateProbe probe;
    private final double min;
    private final double max;
    private final double backstop;

    private ClimateTrigger(ClimateProbe probe, double min, double max, double backstop) {
        this.probe = probe;
        this.min = min;
        this.max = max;
        this.backstop = backstop;
    }

    /** Parses the JSON and returns the carrier trigger Railways Untold will store. */
    public static EventTrigger parseCarrier(JsonObject json) {
        ClimateTrigger trigger = new ClimateTrigger(
                ClimateProbe.fromJson(json),
                GsonHelper.getAsDouble(json, "min", Double.NEGATIVE_INFINITY),
                GsonHelper.getAsDouble(json, "max", Double.POSITIVE_INFINITY),
                GsonHelper.getAsDouble(json, "backstop", Double.NaN));
        long key = NEXT_KEY.getAndIncrement();
        BY_KEY.put(key, trigger);
        return new EventTrigger.GameTime(key);
    }

    public static boolean isCarrierKey(long minTicks) {
        return minTicks < KEY_CEILING;
    }

    /** The climate trigger behind a carrier, or null for any other trigger. */
    public static ClimateTrigger fromCarrier(EventTrigger trigger) {
        if (trigger instanceof EventTrigger.GameTime gameTime && isCarrierKey(gameTime.minTicks())) {
            return BY_KEY.get(gameTime.minTicks());
        }
        return null;
    }

    public static boolean testCarrier(long key, TriggerContext ctx) {
        ClimateTrigger trigger = BY_KEY.get(key);
        return trigger != null && trigger.test(ctx);
    }

    public boolean hasBackstop() {
        return !Double.isNaN(backstop);
    }

    public boolean test(TriggerContext ctx) {
        Double value = sample(ctx.level(), ctx.headPosition());
        return value != null && value >= min && value <= max;
    }

    /** True once the head is past the backstop value (and still under max). */
    public boolean pastBackstop(ServerLevel level, BlockPos pos) {
        if (!hasBackstop()) {
            return false;
        }
        Double value = sample(level, pos);
        return value != null && value >= backstop && value <= max;
    }

    private Double sample(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        try {
            return probe.sample(level, pos.getX(), pos.getZ());
        } catch (RuntimeException e) {
            LOGGER.warn("[Frostline] climate trigger could not sample {} at {}: {}",
                    level.dimension().location(), pos, e.getMessage());
            return null;
        }
    }
}
