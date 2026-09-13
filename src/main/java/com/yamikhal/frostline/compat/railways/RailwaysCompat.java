package com.yamikhal.frostline.compat.railways;

import com.mojang.logging.LogUtils;
import com.vodmordia.railwaysuntold.worldgen.api.TrackAvoidanceApi;
import com.yamikhal.frostline.CorridorDensityFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Frostline's side of Railways Untold. Only loaded when that mod is present: Frostline
 * calls {@link #init} behind a ModList check, and the mixins under
 * com.yamikhal.frostline.mixin.railways are skipped by FrostlineMixinPlugin otherwise.
 *
 * What it changes (see RAILWAYS.md in the datapack for the why):
 *   - the line always runs north-south (InitialChunkSelectorMixin)
 *   - exploration targets are pulled onto the frostline:corridor line (DirectionUtilMixin)
 *   - structure targets must sit within TARGET_CONE_DEGREES of the heading (VillagePredictorMixin)
 *   - small peaks are skirted or cut instead of bored (RouteBridgeTunnelDetectorMixin)
 *   - frostline:climate event trigger, and forced termini (EventDefinitionLoaderMixin,
 *     CoarseRouteExecutionRuleMixin, TerminusBackstop)
 *   - exact footprints of Frostline structures as avoidance zones (StructureFootprints)
 */
public final class RailwaysCompat {

    public static final String MOD_ID = "railwaysuntold";

    /** Railways Untold accepts targets up to 120 degrees off the heading. */
    public static final double TARGET_CONE_DEGREES = 45.0D;
    /** Railways Untold bores any peak rising more than 25 blocks. */
    public static final int TUNNEL_RISE_THRESHOLD = 40;
    /** Share of the way an exploration target moves from its random x to the corridor line. */
    public static final double CORRIDOR_PULL = 0.75D;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile CorridorDensityFunction corridor;

    private RailwaysCompat() {
    }

    public static void init() {
        TrackAvoidanceApi.registerProvider(StructureFootprints::zones);
        MinecraftForge.EVENT_BUS.addListener(RailwaysCompat::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(RailwaysCompat::onServerStopped);
        LOGGER.info("[Frostline] Railways Untold compat enabled");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        corridor = CorridorDensityFunction.find(event.getServer().overworld().getChunkSource().randomState())
                .orElse(null);
        if (corridor == null) {
            LOGGER.warn("[Frostline] overworld has no frostline:corridor; rail exploration is not steered");
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        corridor = null;
    }

    /** Moves a north- or south-bound exploration target most of the way onto the corridor line. */
    public static BlockPos steerToCorridor(BlockPos target, Direction heading) {
        CorridorDensityFunction line = corridor;
        if (line == null || target == null || heading.getAxis() != Direction.Axis.Z) {
            return target;
        }
        double x = Mth.lerp(CORRIDOR_PULL, target.getX(), line.lineX(target.getZ()));
        return new BlockPos(Mth.floor(x), target.getY(), target.getZ());
    }
}
