package com.yamikhal.frostline.compat.railways;

import com.mojang.logging.LogUtils;
import com.vodmordia.railwaysuntold.datapack.AppearanceTracker;
import com.vodmordia.railwaysuntold.datapack.EventDefinition;
import com.vodmordia.railwaysuntold.datapack.EventDefinitionLoader;
import com.vodmordia.railwaysuntold.datapack.EventTrigger;
import com.vodmordia.railwaysuntold.worldgen.head.TrackExpansionHead;
import com.vodmordia.railwaysuntold.worldgen.placement.PlacementDecision;
import com.vodmordia.railwaysuntold.worldgen.placement.decision.DeciderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes sure each end of the line gets its wreck.
 *
 * A terminus is an ordinary Railways Untold event (a jigsaw with a dead_end marker)
 * whose frostline:climate trigger carries a "backstop" value. The trigger alone lets the
 * event fire from "min" on, but events also need a random roll, level ground over the
 * footprint and a cardinal heading, so a line can run past its end. Once the head is
 * past "backstop" this takes over: every placement decision becomes that event, forced
 * past the roll and filters, until it lands.
 *
 * If it keeps failing (steep ground) the head is retired after MAX_FORCED_ATTEMPTS, so it
 * never pushes on into The Quiet or off the escarpment. That is a plain stop, not a
 * wreck; it is logged so the spot can be looked at.
 */
public final class TerminusBackstop {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_FORCED_ATTEMPTS = 12;

    private record Terminus(ResourceLocation id, int maxAppearances, ClimateTrigger trigger) {
    }

    private static volatile List<Terminus> termini = List.of();
    private static final Map<UUID, Integer> ATTEMPTS = new ConcurrentHashMap<>();
    private static volatile ResourceLocation forced;

    private TerminusBackstop() {
    }

    /** Re-index terminus events after every datapack reload. */
    public static void rebuild(List<EventDefinitionLoader.ValidatedEventEntry> entries) {
        List<Terminus> found = new ArrayList<>();
        for (EventDefinitionLoader.ValidatedEventEntry entry : entries) {
            EventDefinition definition = entry.definition();
            for (EventTrigger trigger : definition.triggers()) {
                ClimateTrigger climate = ClimateTrigger.fromCarrier(trigger);
                if (climate != null && climate.hasBackstop()) {
                    found.add(new Terminus(definition.id(), definition.maxAppearances(), climate));
                    break;
                }
            }
        }
        termini = List.copyOf(found);
        ATTEMPTS.clear();
        if (!found.isEmpty()) {
            LOGGER.info("[Frostline] rail termini with backstops: {}",
                    found.stream().map(Terminus::id).toList());
        }
    }

    /** The event id forced by the last decision, cleared on read. */
    public static ResourceLocation consumeForced() {
        ResourceLocation id = forced;
        forced = null;
        return id;
    }

    /** A decision to use instead of the normal pipeline, or null to let it run. */
    public static Optional<PlacementDecision> check(DeciderContext context) {
        List<Terminus> current = termini;
        if (current.isEmpty()) {
            return null;
        }
        TrackExpansionHead head = context.head();
        if (head.isComplete() || head.isDiagonal()) {
            return null;
        }
        ServerLevel level = context.level();
        BlockPos pos = context.start();
        AppearanceTracker appearances = AppearanceTracker.get(level);
        for (Terminus terminus : current) {
            if (!appearances.canAppear(terminus.id(), terminus.maxAppearances())
                    || !terminus.trigger().pastBackstop(level, pos)) {
                continue;
            }
            UUID headId = head.getHeadId();
            int attempts = ATTEMPTS.merge(headId, 1, Integer::sum);
            if (attempts > MAX_FORCED_ATTEMPTS) {
                ATTEMPTS.remove(headId);
                head.markComplete();
                LOGGER.warn("[Frostline] terminus {} could not be placed after {} tries; head {} retired at {}",
                        terminus.id(), MAX_FORCED_ATTEMPTS, head.getHeadNumber(), pos);
                return Optional.of(PlacementDecision.defer());
            }
            forced = terminus.id();
            LOGGER.info("[Frostline] head {} past terminus backstop at {}; forcing {} (try {})",
                    head.getHeadNumber(), pos, terminus.id(), attempts);
            return Optional.of(PlacementDecision.event(pos, context.direction()));
        }
        return null;
    }
}
