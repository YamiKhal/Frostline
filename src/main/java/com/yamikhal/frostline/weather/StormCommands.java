package com.yamikhal.frostline.weather;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;

/** /frostline storm start [days] | stop | status, for testing the schedule. Operators only. */
public final class StormCommands {

    private StormCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("frostline")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("storm")
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("start")
                                .executes(ctx -> start(ctx.getSource(), 1))
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 30))
                                        .executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "days")))))
                        .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))));
    }

    private static int start(CommandSourceStack src, int days) {
        if (!enabled(src)) {
            return 0;
        }
        ServerLevel level = src.getServer().overworld();
        StormState s = StormScheduler.state(level);
        long t = level.getDayTime();
        long windowStart = WeatherConfig.COMMON.windowStart.get();
        long open = Math.floorDiv(t - windowStart, StormScheduler.DAY) * StormScheduler.DAY + windowStart;
        long close = open + StormScheduler.windowLength();
        long end = (t < close ? close : t + StormScheduler.windowLength()) + (days - 1L) * StormScheduler.DAY;
        StormScheduler.startStorm(s, end, true);
        StormScheduler.applyNow(level);
        src.sendSuccess(() -> Component.literal("Storm started, ends in " + ticks(end - t) + "."), true);
        return 1;
    }

    private static int stop(CommandSourceStack src) {
        if (!enabled(src)) {
            return 0;
        }
        ServerLevel level = src.getServer().overworld();
        StormState s = StormScheduler.state(level);
        StormScheduler.endStorm(s, level.getDayTime(), false);
        StormScheduler.applyNow(level);
        src.sendSuccess(() -> Component.literal("Storm stopped."), true);
        return 1;
    }

    private static int status(CommandSourceStack src) {
        ServerLevel level = src.getServer().overworld();
        WeatherConfig.Common c = WeatherConfig.COMMON;
        StormState s = StormScheduler.state(level);
        long t = level.getDayTime();
        StringBuilder out = new StringBuilder();
        if (!c.enabled.get()) {
            out.append("Frostline storms are disabled (vanilla weather).");
        } else {
            if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE)) {
                out.append("doWeatherCycle is off: the schedule is paused.\n");
            }
            if (s.storm) {
                out.append("Storm, day ").append(s.stormDays).append(s.manual ? " (command)" : "")
                        .append(", ends in ").append(ticks(s.stormEnd - t))
                        .append(s.manual ? "." : ", may continue.");
            } else if (s.pendingStart >= 0) {
                out.append("Clear, storm rolled to start in ").append(ticks(s.pendingStart - t)).append('.');
            } else {
                long windowStart = c.windowStart.get();
                long cycle = Math.floorDiv(t - windowStart, StormScheduler.DAY);
                long next = Math.max(cycle + 1, s.cooldownUntilCycle);
                out.append("Clear. Next window roll in ")
                        .append(ticks(next * StormScheduler.DAY + windowStart - t)).append('.');
            }
        }
        src.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static boolean enabled(CommandSourceStack src) {
        if (WeatherConfig.COMMON.enabled.get()) {
            return true;
        }
        src.sendFailure(Component.literal("Frostline storms are disabled in frostline-weather.toml."));
        return false;
    }

    private static String ticks(long ticks) {
        long seconds = Math.max(0, ticks) / 20;
        return seconds >= 60 ? (seconds / 60) + " min " + (seconds % 60) + " s" : seconds + " s";
    }
}
