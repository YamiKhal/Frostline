package com.yamikhal.frostline.compat.railways;

import com.vodmordia.railwaysuntold.worldgen.api.AvoidanceZone;
import com.vodmordia.railwaysuntold.worldgen.head.TrackExpansionHead;
import com.vodmordia.railwaysuntold.worldgen.placement.TrackExpansionOrchestrator;
import com.vodmordia.railwaysuntold.worldgen.placement.TrackPlacerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exact footprints of Frostline structures, fed to Railways Untold as avoidance zones.
 *
 * Railways Untold predicts every structure near a planned route, but only villages get
 * their real piece boxes; everything else is a 48-block circle, which is too small for a
 * jigsaw town and too big for a shack. This supplies the real bounding box of every
 * structure start near an active head, for any structure that is:
 *
 *   - in the frostline namespace, or
 *   - in the structure tag #frostline:rail_avoid (for other mods' structures)
 *
 * The start's bounding box covers every jigsaw piece, so sole NBT structures and jigsaw
 * clusters are handled the same way. Candidate chunks come from seed math
 * (StructurePlacement#isStructureChunk); only those are loaded to STRUCTURE_STARTS, once,
 * and cached. Off the server thread nothing is loaded; cached boxes are still returned.
 */
public final class StructureFootprints {

    public static final TagKey<Structure> RAIL_AVOID =
            TagKey.create(Registries.STRUCTURE, new ResourceLocation("frostline", "rail_avoid"));

    private static final String NAMESPACE = "frostline";
    /** Chunks around each head to report. Railways Untold plans about 800 blocks ahead. */
    private static final int SCAN_RADIUS_CHUNKS = 56;
    /** Extra clearance around each box, so clearing never shaves a wall. */
    private static final int MARGIN = 6;
    private static final int CACHE_LIMIT = 32768;

    private record Relevant(List<Holder<StructureSet>> sets, List<Structure> structures) {
    }

    private static final Map<ServerLevel, Relevant> RELEVANT = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel, Map<Long, List<BoundingBox>>> STARTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private StructureFootprints() {
    }

    public static Collection<AvoidanceZone> zones(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return List.of();
        }
        TrackExpansionOrchestrator orchestrator = TrackPlacerRegistry.get(level);
        if (orchestrator == null) {
            return List.of();
        }
        Relevant relevant = RELEVANT.computeIfAbsent(level, StructureFootprints::collect);
        if (relevant.sets().isEmpty()) {
            return List.of();
        }
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        Map<Long, List<BoundingBox>> cache = STARTS.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        if (cache.size() > CACHE_LIMIT) {
            cache.clear();
        }
        boolean canLoad = level.getServer().isSameThread();

        Map<String, AvoidanceZone> zones = new LinkedHashMap<>();
        for (TrackExpansionHead head : List.copyOf(orchestrator.getHeadManager().getActiveHeads())) {
            ChunkPos centre = new ChunkPos(head.getPosition());
            for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
                for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                    int cx = centre.x + dx;
                    int cz = centre.z + dz;
                    if (!isCandidate(relevant, state, cx, cz)) {
                        continue;
                    }
                    long key = ChunkPos.asLong(cx, cz);
                    List<BoundingBox> boxes = cache.get(key);
                    if (boxes == null) {
                        if (!canLoad) {
                            continue;
                        }
                        boxes = load(level, relevant, cx, cz);
                        cache.put(key, boxes);
                    }
                    for (BoundingBox box : boxes) {
                        zones.putIfAbsent(box.minX() + ":" + box.minY() + ":" + box.minZ(), toZone(box));
                    }
                }
            }
        }
        return List.copyOf(zones.values());
    }

    private static boolean isCandidate(Relevant relevant, ChunkGeneratorStructureState state, int cx, int cz) {
        for (Holder<StructureSet> set : relevant.sets()) {
            if (set.value().placement().isStructureChunk(state, cx, cz)) {
                return true;
            }
        }
        return false;
    }

    private static List<BoundingBox> load(ServerLevel level, Relevant relevant, int cx, int cz) {
        ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS, true);
        List<BoundingBox> boxes = new ArrayList<>();
        for (Structure structure : relevant.structures()) {
            StructureStart start = chunk.getStartForStructure(structure);
            if (start != null && start.isValid()) {
                boxes.add(start.getBoundingBox());
            }
        }
        return List.copyOf(boxes);
    }

    private static Relevant collect(ServerLevel level) {
        List<Holder<StructureSet>> sets = new ArrayList<>();
        List<Structure> structures = new ArrayList<>();
        for (Holder<StructureSet> set : level.getChunkSource().getGeneratorState().possibleStructureSets()) {
            boolean any = false;
            for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
                Holder<Structure> structure = entry.structure();
                boolean ours = structure.unwrapKey()
                        .map(key -> NAMESPACE.equals(key.location().getNamespace()))
                        .orElse(false);
                if (ours || structure.is(RAIL_AVOID)) {
                    any = true;
                    if (!structures.contains(structure.value())) {
                        structures.add(structure.value());
                    }
                }
            }
            if (any) {
                sets.add(set);
            }
        }
        return new Relevant(List.copyOf(sets), List.copyOf(structures));
    }

    private static AvoidanceZone toZone(BoundingBox box) {
        return new AvoidanceZone(
                new BlockPos(box.minX() - MARGIN, box.minY() - 4, box.minZ() - MARGIN),
                new BlockPos(box.maxX() + MARGIN, box.maxY() + 16, box.maxZ() + MARGIN));
    }
}
