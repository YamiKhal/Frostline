package com.yamikhal.frostline.weather;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Two files, both in config/:
 *
 *   frostline-weather.toml         when storms happen. Read by the server (or the integrated
 *                                  server in single player); clients ignore it.
 *   frostline-weather-client.toml  how weather looks and sounds. Per player.
 */
public final class WeatherConfig {

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();
        COMMON = new Common(common);
        COMMON_SPEC = common.build();
        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        CLIENT = new Client(client);
        CLIENT_SPEC = client.build();
    }

    private WeatherConfig() {
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.IntValue firstStormDay;
        public final ForgeConfigSpec.IntValue windowStart;
        public final ForgeConfigSpec.IntValue windowEnd;
        public final ForgeConfigSpec.DoubleValue skipChance;
        public final ForgeConfigSpec.IntValue maxStartDelay;
        public final ForgeConfigSpec.DoubleValue continueChance;
        public final ForgeConfigSpec.IntValue maxStormDays;
        public final ForgeConfigSpec.IntValue cooldownDays;
        public final ForgeConfigSpec.BooleanValue sleepEndsStorm;

        Common(ForgeConfigSpec.Builder b) {
            b.comment(
                    "When snowstorms happen.",
                    "",
                    "Every in-game day has one storm window. When a window opens, a storm is rolled:",
                    "  - skipChance decides whether this window gets no storm at all.",
                    "  - A storm starts somewhere in the first maxStartDelay ticks of the window",
                    "    and is scheduled to end when the window closes.",
                    "  - When it reaches its end, continueChance decides whether it keeps going for one",
                    "    more full day (through the day and the next window). Rolled again each day,",
                    "    until the storm has lasted maxStormDays.",
                    "  - After a storm, cooldownDays windows pass with no storm.",
                    "",
                    "Times are day time in ticks: 0 sunrise, 6000 noon, 12000 sunset, 13000 dusk,",
                    "18000 midnight, 23000 dawn. A window may cross midnight (start 13000, end 1000).",
                    "Equal start and end means the whole day is the window.",
                    "",
                    "Storms are vanilla rain: with the doWeatherCycle game rule off, nothing changes on",
                    "its own. /weather still works (a storm started that way lasts its duration and",
                    "never continues). /frostline storm start|stop|status for testing."
            ).push("storms");
            enabled = b.comment("Frostline schedules overworld weather. false = vanilla weather cycle.")
                    .define("enabled", true);
            firstStormDay = b.comment("No storm before this day (day 0 is the first day of the world).")
                    .defineInRange("firstStormDay", 1, 0, 100000);
            windowStart = b.comment("Day time at which the storm window opens.")
                    .defineInRange("windowStart", 13000, 0, 23999);
            windowEnd = b.comment("Day time at which the storm window closes and a storm normally ends.")
                    .defineInRange("windowEnd", 23000, 0, 23999);
            skipChance = b.comment("Chance that a window passes without a storm. 0 = a storm every window, 1 = never.")
                    .defineInRange("skipChance", 0.5, 0.0, 1.0);
            maxStartDelay = b.comment("A storm starts a random 0..maxStartDelay ticks after the window opens.")
                    .defineInRange("maxStartDelay", 2400, 0, 24000);
            continueChance = b.comment("Chance, when a storm reaches its end, that it continues for one more full day.")
                    .defineInRange("continueChance", 0.2, 0.0, 1.0);
            maxStormDays = b.comment("A storm never lasts longer than this many days, whatever continueChance rolls.")
                    .defineInRange("maxStormDays", 3, 1, 30);
            cooldownDays = b.comment("Windows skipped after a storm ends before another may start.")
                    .defineInRange("cooldownDays", 1, 0, 30);
            sleepEndsStorm = b.comment("Sleeping through the night ends a storm (vanilla sleeping clears rain). false = the storm carries on.")
                    .define("sleepEndsStorm", false);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> excludedDimensions;
        public final ForgeConfigSpec.DoubleValue calmSnowfall;
        public final ForgeConfigSpec.DoubleValue stormSnowfall;
        public final ForgeConfigSpec.DoubleValue calmDensity;
        public final ForgeConfigSpec.DoubleValue stormDensity;
        public final ForgeConfigSpec.IntValue stormRadius;
        public final ForgeConfigSpec.DoubleValue windSlant;
        public final ForgeConfigSpec.IntValue stormFadeSeconds;
        public final ForgeConfigSpec.DoubleValue windsweptIntensity;

        public final ForgeConfigSpec.BooleanValue fog;
        public final ForgeConfigSpec.IntValue stormFogDistance;
        public final ForgeConfigSpec.ConfigValue<String> fogColorDay;
        public final ForgeConfigSpec.ConfigValue<String> fogColorNight;

        public final ForgeConfigSpec.DoubleValue stormWindVolume;
        public final ForgeConfigSpec.DoubleValue calmWindVolume;
        public final ForgeConfigSpec.DoubleValue shelteredVolume;
        public final ForgeConfigSpec.DoubleValue stormAmbienceVolume;

        public final ForgeConfigSpec.DoubleValue sifting;
        public final ForgeConfigSpec.DoubleValue drifting;
        public final ForgeConfigSpec.IntValue particleSamples;

        Client(ForgeConfigSpec.Builder b) {
            b.comment(
                    "How Frostline weather looks and sounds. Everything here is client-side.",
                    "",
                    "Snowfall follows the biome: it only falls where vanilla would snow. Outside a storm",
                    "a light snowfall is always drawn; during a storm it thickens, slants with the wind,",
                    "fog closes in and the wind picks up. Biomes in the #frostline:windswept tag are",
                    "never fully calm."
            ).push("snowfall");
            enabled = b.comment("Frostline weather rendering, fog, wind sound and snow particles. false = vanilla weather visuals.")
                    .define("enabled", true);
            excludedDimensions = b.comment("Dimensions that keep vanilla weather visuals, e.g. [\"frostline:zone_one\"].")
                    .defineListAllowEmpty(List.of("excludedDimensions"), List::of, o -> o instanceof String);
            calmSnowfall = b.comment("Opacity of the light snowfall outside storms. 0 = none.")
                    .defineInRange("calmSnowfall", 0.35, 0.0, 1.0);
            stormSnowfall = b.comment("Opacity of the storm's dense near layer. 0 = storms only thicken the light snowfall.")
                    .defineInRange("stormSnowfall", 1.0, 0.0, 1.0);
            calmDensity = b.comment("How many flakes fall outside storms: share of snowfall columns drawn. 1 = all, 0.5 = half, 0 = none.")
                    .defineInRange("calmDensity", 0.6, 0.0, 1.0);
            stormDensity = b.comment("How many flakes fall in a full storm: share of snowfall columns drawn (both layers). Eases from calmDensity as the storm builds.")
                    .defineInRange("stormDensity", 0.7, 0.0, 1.0);
            stormRadius = b.comment("Blocks around you that snowfall is drawn in during a storm (vanilla fancy draws 10).")
                    .defineInRange("stormRadius", 12, 4, 16);
            windSlant = b.comment("How far storm snow slants with the wind. 0 = straight down.")
                    .defineInRange("windSlant", 0.5, 0.0, 1.0);
            stormFadeSeconds = b.comment("Seconds for a storm to build up or die down.")
                    .defineInRange("stormFadeSeconds", 30, 1, 600);
            windsweptIntensity = b.comment("Storm intensity that #frostline:windswept biomes never drop below. 0 = no floor.")
                    .defineInRange("windsweptIntensity", 0.35, 0.0, 1.0);
            b.pop();

            b.push("fog");
            fog = b.comment("Storms bring fog while you are outdoors.")
                    .define("enabled", true);
            stormFogDistance = b.comment("How far you can see in a full storm, in blocks.")
                    .defineInRange("stormFogDistance", 40, 8, 512);
            fogColorDay = b.comment("Storm fog colour by day, #RRGGBB.")
                    .define("colorDay", "#C3CBD3", WeatherConfig::isColor);
            fogColorNight = b.comment("Storm fog colour at night, #RRGGBB.")
                    .define("colorNight", "#1C2229", WeatherConfig::isColor);
            b.pop();

            b.push("sound");
            stormWindVolume = b.comment("Wind volume in a full storm, outdoors. Also scaled by the Weather volume slider.")
                    .defineInRange("stormWindVolume", 0.45, 0.0, 1.0);
            calmWindVolume = b.comment("Wind volume outside storms, outdoors in snowy biomes. 0 = silent.")
                    .defineInRange("calmWindVolume", 0.0, 0.0, 1.0);
            shelteredVolume = b.comment("Share of the wind volume still heard indoors.")
                    .defineInRange("shelteredVolume", 0.25, 0.0, 1.0);
            stormAmbienceVolume = b.comment("Volume of the biome's own ambience (its background loop and additions) in a full storm.",
                            "It fades with the storm and returns as it passes. 0 = silent during storms, 1 = unchanged. Cave mood sounds are not affected.")
                    .defineInRange("stormAmbienceVolume", 0.0, 0.0, 1.0);
            b.pop();

            b.push("particles");
            sifting = b.comment("Snow sifting down from the undersides of snow blocks, powder snow and snow-covered leaves. 0 = off.")
                    .defineInRange("sifting", 1.0, 0.0, 4.0);
            drifting = b.comment("Snow blown off the ground by the wind, mostly in storms. 0 = off.")
                    .defineInRange("drifting", 1.0, 0.0, 4.0);
            particleSamples = b.comment("Blocks checked around you per tick for the two effects above. Cost scales with this.")
                    .defineInRange("samplesPerTick", 300, 0, 2000);
            b.pop();
        }
    }

    private static boolean isColor(Object o) {
        return o instanceof String s && parseColor(s, -1) != -1;
    }

    public static int parseColor(String s, int fallback) {
        if (s == null || !s.matches("#[0-9a-fA-F]{6}")) {
            return fallback;
        }
        return Integer.parseInt(s.substring(1), 16);
    }
}
