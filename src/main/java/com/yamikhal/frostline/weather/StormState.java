package com.yamikhal.frostline.weather;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** The storm schedule's memory, saved with the overworld as data/frostline_weather.dat. */
public final class StormState extends SavedData {

    static final String NAME = "frostline_weather";
    private static final long NEVER = Long.MIN_VALUE / 4;

    /** Frostline has taken over the vanilla weather timers. */
    boolean controlling;
    boolean storm;
    /** Started or stopped by a command: never continues, no cooldown. */
    boolean manual;
    /** Day time at which the storm ends (or rolls to continue). */
    long stormEnd;
    int stormDays;
    /** A rolled storm waiting for its start delay, and the window close it will end at. */
    long pendingStart = -1;
    long pendingClose;
    /** Last window cycle that has been rolled. */
    long decidedCycle = NEVER;
    /** First window cycle a new storm may start in. */
    long cooldownUntilCycle = NEVER;
    long lastTime;

    static StormState load(CompoundTag tag) {
        StormState s = new StormState();
        s.controlling = tag.getBoolean("controlling");
        s.storm = tag.getBoolean("storm");
        s.manual = tag.getBoolean("manual");
        s.stormEnd = tag.getLong("stormEnd");
        s.stormDays = tag.getInt("stormDays");
        s.pendingStart = tag.getLong("pendingStart");
        s.pendingClose = tag.getLong("pendingClose");
        s.decidedCycle = tag.getLong("decidedCycle");
        s.cooldownUntilCycle = tag.getLong("cooldownUntilCycle");
        s.lastTime = tag.getLong("lastTime");
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("controlling", controlling);
        tag.putBoolean("storm", storm);
        tag.putBoolean("manual", manual);
        tag.putLong("stormEnd", stormEnd);
        tag.putInt("stormDays", stormDays);
        tag.putLong("pendingStart", pendingStart);
        tag.putLong("pendingClose", pendingClose);
        tag.putLong("decidedCycle", decidedCycle);
        tag.putLong("cooldownUntilCycle", cooldownUntilCycle);
        tag.putLong("lastTime", lastTime);
        return tag;
    }
}
