package com.yamikhal.frostline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yamikhal.Frostline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * All-or-nothing placement gate for a jigsaw piece: the conditions a placed feature gets from
 * its placement modifiers, for templates, which have none.
 *
 * Why this is Java: a feature is a list of conditions (block_predicate_filter,
 * height_range, rarity_filter, surface_relative_threshold_filter) wrapped around one
 * placement. A structure has `biomes`, `spacing`, `separation` and nothing else - no ground
 * check, no height band, no thinning that is not the spread grid. Worse, a structure start is
 * projected onto the WG heightmap, the noise surface, which is decided before the lakes step
 * puts ice under it; so even a hypothetical JSON check would be reading terrain that is not
 * what the piece finally lands on.
 *
 * A processor is the first moment the real world is visible, and finalizeProcessing sees the
 * whole piece at once, so returning an empty list is a veto of the piece rather than a
 * per-block edit.
 *
 * Model:
 *   columns    the piece's blocks grouped by X/Z; each column's lowest block is the footprint
 *   probe      from one block under the footprint, walk down at most max_drop through air,
 *              snow layers and tree-replaceable plants; the first other block is that
 *              column's ground. A fluid always stops the walk and fails the column.
 *   verdict    every condition below is checked against those columns, and any failure drops
 *              the whole piece.
 *
 * Conditions, all optional, all skipped when absent:
 *   ground        blocks the piece may stand on (a tag or a list). Like a feature's
 *                 block_predicate_filter / matching_blocks.
 *   forbidden     blocks it may never stand on, checked after `ground`, so a wide `ground`
 *                 can be cut down without restating it.
 *   max_drop      how far under the footprint to look for that ground (default 3). A column
 *                 with no ground inside it fails.
 *   min_fraction  share of columns that must pass the ground test (default 1.0, every one).
 *   min_y, max_y  the band the ground must sit in, inclusive. Absolute world Y, tested
 *                 against the lowest ground under the piece.
 *   max_slope     largest Y spread allowed between the columns' grounds. Features cannot ask
 *                 this at all - they are one column - and it is what keeps a prop off a cliff
 *                 edge without beard-ing the terrain.
 *   clearance     free blocks required above the piece's top block in every column.
 *   clear_footprint  every position the piece would write must currently be air, a replaceable
 *                 plant or snow: the piece never carves into terrain or another structure.
 *   chance        thinning roll, 0-1, from the piece's own position and `salt`. The
 *                 rarity_filter a structure set cannot express without widening its spread.
 *   salt          keeps two lists using `chance` from rolling the same pieces.
 *   yield_to      structures (a tag or a list) this piece gives way to: if any of their pieces'
 *                 bounding boxes overlaps this piece's box, widened by yield_margin, this piece
 *                 is dropped. Listing the piece's own structure spaces copies of it apart; the
 *                 copy whose start chunk sorts first keeps its place. Never let two different
 *                 structures yield to each other - both would drop.
 *   yield_margin  blocks of horizontal gap required around the piece for yield_to (default 0).
 *
 * yield_to reads structure starts, not blocks, so the answer is fixed before anything places:
 * the same in every chunk a piece crosses and whichever structure places first. The cost is
 * that it yields to the other piece's whole box, air included, and to a piece that its own
 * filter later drops.
 *
 * Cost: one pass over the piece's blocks, then at most (max_drop + clearance + 1) block reads
 * per column, once, when the piece places. A 7x3 fallen tree is about fifty reads. Keep
 * `max_drop` and `clearance` small and this never shows up next to the block writes it guards.
 *
 * Known edge: a piece that straddles a chunk border is handed to the processor once per chunk,
 * already clipped, so each half is judged on its own columns. Pieces small enough to sit in one
 * chunk most of the time (props, logs, boulders) are unaffected in practice.
 */
public class PlacementFilterProcessor extends StructureProcessor {

    public static final Codec<PlacementFilterProcessor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.BLOCK).optionalFieldOf("ground")
                    .forGetter((PlacementFilterProcessor p) -> p.ground),
            RegistryCodecs.homogeneousList(Registries.BLOCK).optionalFieldOf("forbidden")
                    .forGetter((PlacementFilterProcessor p) -> p.forbidden),
            Codec.intRange(0, 16).optionalFieldOf("max_drop", 3)
                    .forGetter((PlacementFilterProcessor p) -> p.maxDrop),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("min_fraction", 1.0F)
                    .forGetter((PlacementFilterProcessor p) -> p.minFraction),
            Codec.INT.optionalFieldOf("min_y")
                    .forGetter((PlacementFilterProcessor p) -> p.minY),
            Codec.INT.optionalFieldOf("max_y")
                    .forGetter((PlacementFilterProcessor p) -> p.maxY),
            Codec.intRange(0, 64).optionalFieldOf("max_slope")
                    .forGetter((PlacementFilterProcessor p) -> p.maxSlope),
            Codec.intRange(0, 32).optionalFieldOf("clearance", 0)
                    .forGetter((PlacementFilterProcessor p) -> p.clearance),
            Codec.BOOL.optionalFieldOf("clear_footprint", false)
                    .forGetter((PlacementFilterProcessor p) -> p.clearFootprint),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F)
                    .forGetter((PlacementFilterProcessor p) -> p.chance),
            Codec.INT.optionalFieldOf("salt", 0)
                    .forGetter((PlacementFilterProcessor p) -> p.salt),
            RegistryCodecs.homogeneousList(Registries.STRUCTURE).optionalFieldOf("yield_to")
                    .forGetter((PlacementFilterProcessor p) -> p.yieldTo),
            Codec.intRange(0, 16).optionalFieldOf("yield_margin", 0)
                    .forGetter((PlacementFilterProcessor p) -> p.yieldMargin)
    ).apply(instance, PlacementFilterProcessor::new));

    private static final List<StructureTemplate.StructureBlockInfo> VETO = List.of();

    private final Optional<HolderSet<Block>> ground;
    private final Optional<HolderSet<Block>> forbidden;
    private final int maxDrop;
    private final float minFraction;
    private final Optional<Integer> minY;
    private final Optional<Integer> maxY;
    private final Optional<Integer> maxSlope;
    private final int clearance;
    private final boolean clearFootprint;
    private final float chance;
    private final int salt;
    private final Optional<HolderSet<Structure>> yieldTo;
    private final int yieldMargin;

    public PlacementFilterProcessor(
            Optional<HolderSet<Block>> ground,
            Optional<HolderSet<Block>> forbidden,
            int maxDrop,
            float minFraction,
            Optional<Integer> minY,
            Optional<Integer> maxY,
            Optional<Integer> maxSlope,
            int clearance,
            boolean clearFootprint,
            float chance,
            int salt,
            Optional<HolderSet<Structure>> yieldTo,
            int yieldMargin) {
        this.ground = ground;
        this.forbidden = forbidden;
        this.maxDrop = maxDrop;
        this.minFraction = minFraction;
        this.minY = minY;
        this.maxY = maxY;
        this.maxSlope = maxSlope;
        this.clearance = clearance;
        this.clearFootprint = clearFootprint;
        this.chance = chance;
        this.salt = salt;
        this.yieldTo = yieldTo;
        this.yieldMargin = yieldMargin;
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level,
            BlockPos pieceOrigin,
            BlockPos structureOrigin,
            List<StructureTemplate.StructureBlockInfo> original,
            List<StructureTemplate.StructureBlockInfo> processed,
            StructurePlaceSettings settings) {

        if (processed.isEmpty()) {
            return processed;
        }

        // lowest and highest written block per column: what the piece stands on, and its roof
        Map<Long, BlockPos> footprint = new HashMap<>();
        Map<Long, Integer> roof = new HashMap<>();
        BlockPos anchor = null;
        for (StructureTemplate.StructureBlockInfo info : processed) {
            BlockPos pos = info.pos();
            long key = columnKey(pos);
            BlockPos low = footprint.get(key);
            if (low == null || pos.getY() < low.getY()) {
                footprint.put(key, pos);
            }
            Integer high = roof.get(key);
            if (high == null || pos.getY() > high) {
                roof.put(key, pos.getY());
            }
            if (anchor == null || below(pos, anchor)) {
                anchor = pos;
            }
        }

        if (this.chance < 1.0F && roll(anchor) >= this.chance) {
            return VETO;
        }

        if (this.yieldTo.isPresent() && level instanceof WorldGenRegion region
                && yields(region, pieceOrigin, anchor)) {
            return VETO;
        }

        if (this.clearFootprint) {
            for (StructureTemplate.StructureBlockInfo info : processed) {
                if (!passThrough(level.getBlockState(info.pos()))) {
                    return VETO;
                }
            }
        }

        int passed = 0;
        int lowestGround = Integer.MAX_VALUE;
        int highestGround = Integer.MIN_VALUE;
        List<BlockPos> columns = new ArrayList<>(footprint.values());
        for (BlockPos column : columns) {
            int groundY = probe(level, column);
            if (groundY == NO_GROUND) {
                continue;
            }
            passed++;
            lowestGround = Math.min(lowestGround, groundY);
            highestGround = Math.max(highestGround, groundY);
        }

        if (passed < Math.ceil(columns.size() * this.minFraction) || passed == 0) {
            return VETO;
        }
        if (this.minY.isPresent() && lowestGround < this.minY.get()) {
            return VETO;
        }
        if (this.maxY.isPresent() && lowestGround > this.maxY.get()) {
            return VETO;
        }
        if (this.maxSlope.isPresent() && highestGround - lowestGround > this.maxSlope.get()) {
            return VETO;
        }

        if (this.clearance > 0) {
            for (Map.Entry<Long, Integer> entry : roof.entrySet()) {
                BlockPos column = footprint.get(entry.getKey());
                BlockPos.MutableBlockPos above = column.mutable().setY(entry.getValue());
                for (int i = 0; i < this.clearance; i++) {
                    above.move(0, 1, 0);
                    if (level.isOutsideBuildHeight(above) || !passThrough(level.getBlockState(above))) {
                        return VETO;
                    }
                }
            }
        }

        return processed;
    }

    private static final int NO_GROUND = Integer.MIN_VALUE;

    /**
     * True when a structure in yield_to has a piece overlapping this one. The piece is found
     * among the starts referenced by the chunk being written, by its placement origin; every
     * chunk under its widened box is then searched, since a piece can only overlap it where
     * some such chunk references it.
     */
    private boolean yields(WorldGenRegion region, BlockPos pieceOrigin, BlockPos written) {
        StructureManager structures = region.getLevel().structureManager().forWorldGenRegion(region);

        StructureStart ownStart = null;
        BoundingBox ownBox = null;
        for (StructureStart start : structures.startsForStructure(new ChunkPos(written), s -> true)) {
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof PoolElementStructurePiece pool && pool.getPosition().equals(pieceOrigin)) {
                    ownStart = start;
                    ownBox = piece.getBoundingBox();
                }
            }
        }
        if (ownStart == null) {
            return false;
        }

        int m = this.yieldMargin;
        BoundingBox box = new BoundingBox(ownBox.minX() - m, ownBox.minY(), ownBox.minZ() - m,
                ownBox.maxX() + m, ownBox.maxY(), ownBox.maxZ() + m);
        var registry = region.registryAccess().registryOrThrow(Registries.STRUCTURE);
        HolderSet<Structure> yieldTo = this.yieldTo.get();
        Set<StructureStart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(ownStart);

        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                for (StructureStart start : structures.startsForStructure(new ChunkPos(cx, cz), s -> true)) {
                    if (!seen.add(start)) {
                        continue;
                    }
                    Structure other = start.getStructure();
                    Holder<Structure> holder = registry.wrapAsHolder(other);
                    if (!yieldTo.contains(holder)) {
                        continue;
                    }
                    if (other == ownStart.getStructure() && !before(start.getChunkPos(), ownStart.getChunkPos())) {
                        continue;
                    }
                    for (StructurePiece piece : start.getPieces()) {
                        if (piece.getBoundingBox().intersects(box)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Stable order between two starts of one structure: the earlier one keeps its place. */
    private static boolean before(ChunkPos a, ChunkPos b) {
        return a.x != b.x ? a.x < b.x : a.z < b.z;
    }

    /** Y of the ground carrying this column, or NO_GROUND when there is none it may stand on. */
    private int probe(ServerLevelAccessor level, BlockPos footprint) {
        BlockPos.MutableBlockPos pos = footprint.mutable();
        for (int drop = 0; drop <= this.maxDrop; drop++) {
            pos.move(0, -1, 0);
            if (level.isOutsideBuildHeight(pos)) {
                return NO_GROUND;
            }
            BlockState state = level.getBlockState(pos);
            if (passThrough(state)) {
                continue;
            }
            if (this.ground.isPresent() && !state.is(this.ground.get())) {
                return NO_GROUND;
            }
            if (this.forbidden.isPresent() && state.is(this.forbidden.get())) {
                return NO_GROUND;
            }
            return pos.getY();
        }
        return NO_GROUND;
    }

    /** Cover a piece may sit in or be walked through. Never a fluid: water and ice both stop here. */
    private static boolean passThrough(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isAir()
                || state.is(Blocks.SNOW)
                || state.is(BlockTags.REPLACEABLE_BY_TREES);
    }

    /** Stable 0-1 roll for this piece: same position and salt, same answer, every regeneration. */
    private float roll(BlockPos anchor) {
        long h = anchor.getX() * 3129871L ^ anchor.getZ() * 116129781L ^ anchor.getY() * 9871L;
        h = h * h * 42317861L + h * 11L + this.salt * 132897987541L;
        h ^= h >>> 29;
        return (float) ((h >>> 24) & 0xFFFFFFL) / (float) 0x1000000;
    }

    private static boolean below(BlockPos candidate, BlockPos current) {
        if (candidate.getY() != current.getY()) {
            return candidate.getY() < current.getY();
        }
        if (candidate.getX() != current.getX()) {
            return candidate.getX() < current.getX();
        }
        return candidate.getZ() < current.getZ();
    }

    private static long columnKey(BlockPos pos) {
        return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level,
            BlockPos pieceOrigin,
            BlockPos structureOrigin,
            StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current,
            StructurePlaceSettings settings) {
        // the whole piece is judged in finalizeProcessing; single blocks pass untouched
        return current;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return Frostline.PLACEMENT_FILTER.get();
    }
}
