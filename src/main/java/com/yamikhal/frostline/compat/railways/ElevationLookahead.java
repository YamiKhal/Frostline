package com.yamikhal.frostline.compat.railways;

import com.vodmordia.railwaysuntold.config.RailwaysUntoldConfig;
import com.vodmordia.railwaysuntold.worldgen.planner.noise.CoarseRoute;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Keeps a line up between hills instead of riding every dip.
 *
 * Railways Untold follows the ground and only bridges a dip at least 12 blocks deep with a
 * steep descent, so hill, shallow valley, hill becomes a roller coaster. This pass runs on
 * the coarse route right after its own bridge and tunnel detection and, for every movable
 * waypoint, looks LOOKAHEAD_BLOCKS both ways:
 *
 *   hold = min(highest track behind, highest track ahead)
 *   y'   = max(y, hold)
 *
 * If there is higher track both behind and ahead within the window, the line holds the lower
 * of those two heights and bridges across. If the ground ahead stays lower, "ahead" is low and
 * the line comes down as the window slides, starting LOOKAHEAD_BLOCKS early. Two raise-only
 * sweeps then limit the grade, so every change ramps in at no more than the slope budget.
 *
 * Only ever raises movable waypoints (terrain-follow and flat-maintain, preference basis);
 * bridges, tunnels, stations and route ends are untouched. Raised waypoints become
 * constraints so later passes don't pull them back down, and those standing BRIDGE_CLEARANCE
 * above the ground are typed BRIDGE, which gets decking and piers. O(n) with monotonic queues.
 */
public final class ElevationLookahead {

    /** Blocks looked ahead and behind. */
    public static final int LOOKAHEAD_BLOCKS = 200;
    /** Railways Untold's coarse waypoint spacing (CoarseRoutePlanner.SAMPLE_INTERVAL). */
    private static final int SAMPLE_INTERVAL = 8;
    /** Height above ground at which a held waypoint is typed as a bridge. */
    private static final int BRIDGE_CLEARANCE = 4;

    private ElevationLookahead() {
    }

    public static void apply(List<CoarseRoute.CoarseWaypoint> waypoints) {
        int n = waypoints.size();
        if (n < 3) {
            return;
        }
        int window = Math.max(1, LOOKAHEAD_BLOCKS / SAMPLE_INTERVAL);
        int budget = Math.max(1, (int) Math.ceil(SAMPLE_INTERVAL * RailwaysUntoldConfig.getMaxSlopeRatio()));

        int[] y = new int[n];
        boolean[] movable = new boolean[n];
        for (int i = 0; i < n; i++) {
            CoarseRoute.CoarseWaypoint wp = waypoints.get(i);
            y[i] = wp.advisedTrackY();
            movable[i] = i > 0 && i < n - 1
                    && wp.yBasis() != CoarseRoute.YBasis.CONSTRAINT
                    && (wp.type() == CoarseRoute.WaypointType.TERRAIN_FOLLOW
                    || wp.type() == CoarseRoute.WaypointType.FLAT_MAINTAIN);
        }

        int[] behind = slidingMax(y, window, false);
        int[] ahead = slidingMax(y, window, true);
        int[] target = y.clone();
        for (int i = 0; i < n; i++) {
            if (movable[i]) {
                target[i] = Math.max(y[i], Math.min(behind[i], ahead[i]));
            }
        }
        for (int i = 1; i < n; i++) {
            if (movable[i]) {
                target[i] = Math.max(target[i], target[i - 1] - budget);
            }
        }
        for (int i = n - 2; i >= 0; i--) {
            if (movable[i]) {
                target[i] = Math.max(target[i], target[i + 1] - budget);
            }
        }

        for (int i = 0; i < n; i++) {
            if (!movable[i] || target[i] <= y[i]) {
                continue;
            }
            CoarseRoute.CoarseWaypoint wp = waypoints.get(i);
            CoarseRoute.WaypointType type = target[i] - wp.position().getY() >= BRIDGE_CLEARANCE
                    ? CoarseRoute.WaypointType.BRIDGE
                    : wp.type();
            waypoints.set(i, new CoarseRoute.CoarseWaypoint(
                    wp.position(), target[i], type, CoarseRoute.YBasis.CONSTRAINT));
        }
    }

    /** Max of values over [i - window, i] (forward = false) or [i, i + window] (forward = true). */
    private static int[] slidingMax(int[] values, int window, boolean forward) {
        int n = values.length;
        int[] out = new int[n];
        Deque<Integer> queue = new ArrayDeque<>();
        for (int step = 0; step < n; step++) {
            int i = forward ? n - 1 - step : step;
            while (!queue.isEmpty() && values[queue.peekLast()] <= values[i]) {
                queue.pollLast();
            }
            queue.addLast(i);
            int first = queue.peekFirst();
            if (Math.abs(first - i) > window) {
                queue.pollFirst();
            }
            out[i] = values[queue.peekFirst()];
        }
        return out;
    }
}
