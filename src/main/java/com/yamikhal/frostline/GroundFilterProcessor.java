package com.yamikhal.frostline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yamikhal.Frostline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structure processor that cancels a whole template unless the ground under it is made of
 * the blocks the datapack names.
 *
 * Why this is Java: features check their ground with a block_predicate_filter
 * (minecraft:forest_rock only lands on dirt, the boulder crust disk only targets its
 * matching_blocks list), but a jigsaw structure has no such check. Worse, a structure
 * start is projected onto the WG heightmap, which is the noise surface: it does not know
 * that the lakes step later filled the hollow below it with ice. So a fallen tree lands on
 * a frozen lake and there is no datapack field that can say no.
 *
 * A processor runs at block-placement time, against the real world, after the lakes step,
 * which is the first moment that ice is visible. finalizeProcessing sees the whole piece at
 * once, so it can return an empty list and the piece places nothing at all: a veto, not a
 * per-block edit.
 *
 * Model:
 *   columns    the processed blocks grouped by X/Z; each column's lowest block is the
 *              piece's footprint there
 *   probe      from one block under that, walk down at most max_drop, stepping through air,
 *              snow layers and tree-replaceable plants; the first other block is that
 *              column's ground
 *   verdict    a column passes when its ground is in "ground". Below min_fraction passing
 *              columns, or a column with no ground inside max_drop, the piece is dropped.
 *
 * Fluids are never stepped through, so water and the ice capping it both fail unless the
 * datapack lists them.
 *
 *   ground        blocks the template may sit on; a block tag or a list
 *   max_drop      how far under the footprint to look for that ground
 *   min_fraction  share of columns that must pass, 1.0 meaning every one of them
 */
public class GroundFilterProcessor extends StructureProcessor {

    public static final Codec<GroundFilterProcessor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("ground")
                    .forGetter((GroundFilterProcessor p) -> p.ground),
            Codec.intRange(0, 16).optionalFieldOf("max_drop", 3)
                    .forGetter((GroundFilterProcessor p) -> p.maxDrop),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("min_fraction", 1.0F)
                    .forGetter((GroundFilterProcessor p) -> p.minFraction)
    ).apply(instance, GroundFilterProcessor::new));

    private final HolderSet<Block> ground;
    private final int maxDrop;
    private final float minFraction;

    public GroundFilterProcessor(HolderSet<Block> ground, int maxDrop, float minFraction) {
        this.ground = ground;
        this.maxDrop = maxDrop;
        this.minFraction = minFraction;
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

        // lowest placed block per column; that is what the piece stands on
        Map<Long, BlockPos> footprint = new HashMap<>();
        for (StructureTemplate.StructureBlockInfo info : processed) {
            BlockPos pos = info.pos();
            long key = columnKey(pos);
            BlockPos current = footprint.get(key);
            if (current == null || pos.getY() < current.getY()) {
                footprint.put(key, pos);
            }
        }

        int passed = 0;
        for (BlockPos pos : footprint.values()) {
            if (standsOnGround(level, pos)) {
                passed++;
            }
        }

        if (passed < Math.ceil(footprint.size() * this.minFraction)) {
            return List.of();
        }
        return processed;
    }

    private boolean standsOnGround(ServerLevelAccessor level, BlockPos footprint) {
        BlockPos.MutableBlockPos probe = footprint.mutable();
        for (int drop = 0; drop <= this.maxDrop; drop++) {
            probe.move(0, -1, 0);
            if (level.isOutsideBuildHeight(probe)) {
                return false;
            }
            BlockState state = level.getBlockState(probe);
            if (passThrough(state)) {
                continue;
            }
            return state.is(this.ground);
        }
        return false;
    }

    /** Cover the template can sit in without it counting as ground. Never a fluid. */
    private static boolean passThrough(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isAir()
                || state.is(Blocks.SNOW)
                || state.is(BlockTags.REPLACEABLE_BY_TREES);
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
        return Frostline.GROUND_FILTER.get();
    }
}
