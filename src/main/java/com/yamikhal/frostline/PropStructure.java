package com.yamikhal.frostline;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yamikhal.Frostline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.Optional;

/**
 * A single-piece jigsaw structure that starts at a random block inside its chunk, not at the
 * chunk's corner.
 *
 * Why this is Java: minecraft:jigsaw builds its start from ChunkPos.getMinBlockX/Z with no
 * offset, and no jigsaw field moves it. Every jigsaw prop in the world therefore starts on the
 * 16-block lattice of chunk corners, and a rigid piece rotates about that corner, so two props
 * that pick the same chunk share an anchor block and have four ways to turn. That is the
 * "boulder under a dead tree, grave under a trunk, everything in the same spots" pattern.
 *
 * Randomness: the offset, rotation and pool pick come from a random hashed from world seed,
 * chunk and this structure's own id, so they are stable across regeneration and independent
 * between structures. Vanilla's context random is seeded by chunk alone, which would hand every
 * structure in the chunk the same offset and put them back on one block.
 *
 * Fields: jigsaw's single-piece subset. start_pool, start_height, project_start_to_heightmap,
 * max_distance_from_center, plus the usual biomes / step / terrain_adaptation / spawn_overrides.
 * Height is sampled at the piece's own centre (JigsawPlacement does that), so a prop sits on the
 * ground under it, not on the ground at the chunk corner. Vanilla sets the piece's bottom layer ON
 * the top ground block (ground level delta 1): start_height 0 replaces the surface block with
 * layer 0, and a piece whose ground layer is template layer N wants start_height -N.
 *
 *   max_slope  optional. Largest spread of the projection heightmap across the piece's four
 *              corners and centre; a start on steeper ground is not made at all. This is the
 *              slope test for buried pieces, whose lowest blocks all sit at one Y so the
 *              placement_filter's block probe cannot see the slope. It reads the noise surface,
 *              so it is the same answer every time and on both sides of a chunk border.
 *
 * With max_slope set, biome is checked here, at the piece's own position, before the five slope
 * reads, so chunks outside the biome never pay for them. Vanilla checks the same position again
 * afterwards; the answer is identical.
 */
public class PropStructure extends Structure {

    public static final Codec<PropStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            StructureTemplatePool.CODEC.fieldOf("start_pool")
                    .forGetter((PropStructure s) -> s.startPool),
            HeightProvider.CODEC.fieldOf("start_height")
                    .forGetter((PropStructure s) -> s.startHeight),
            Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap")
                    .forGetter((PropStructure s) -> s.projectStartToHeightmap),
            Codec.intRange(1, 128).optionalFieldOf("max_distance_from_center", 16)
                    .forGetter((PropStructure s) -> s.maxDistanceFromCenter),
            Codec.intRange(0, 64).optionalFieldOf("max_slope")
                    .forGetter((PropStructure s) -> s.maxSlope)
    ).apply(instance, PropStructure::new));

    private final Holder<StructureTemplatePool> startPool;
    private final HeightProvider startHeight;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistanceFromCenter;
    private final Optional<Integer> maxSlope;

    public PropStructure(
            StructureSettings settings,
            Holder<StructureTemplatePool> startPool,
            HeightProvider startHeight,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            Optional<Integer> maxSlope) {
        super(settings);
        this.startPool = startPool;
        this.startHeight = startHeight;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.maxSlope = maxSlope;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();

        ResourceLocation id = context.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(this);
        WorldgenRandom random = random(context.seed(), chunk, id == null ? 0 : id.toString().hashCode());

        GenerationContext own = new GenerationContext(
                context.registryAccess(), context.chunkGenerator(), context.biomeSource(),
                context.randomState(), context.structureTemplateManager(), random, context.seed(),
                chunk, context.heightAccessor(), context.validBiome());

        int y = this.startHeight.sample(random, new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()));
        BlockPos start = new BlockPos(chunk.getMinBlockX() + random.nextInt(16), y, chunk.getMinBlockZ() + random.nextInt(16));

        Optional<GenerationStub> stub = JigsawPlacement.addPieces(own, this.startPool, Optional.empty(), 1, start,
                false, this.projectStartToHeightmap, this.maxDistanceFromCenter);
        if (stub.isEmpty() || this.maxSlope.isEmpty() || this.projectStartToHeightmap.isEmpty()) {
            return stub;
        }

        BlockPos at = stub.get().position();
        Holder<Biome> biome = context.chunkGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(at.getX()), QuartPos.fromBlock(at.getY()), QuartPos.fromBlock(at.getZ()),
                context.randomState().sampler());
        if (!context.validBiome().test(biome)) {
            return Optional.empty();
        }

        StructurePiecesBuilder pieces = stub.get().getPiecesBuilder();
        if (slope(context, pieces.getBoundingBox()) > this.maxSlope.get()) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(at, Either.right(pieces)));
    }

    /** Spread of the projection heightmap over the box's corners and centre. */
    private int slope(GenerationContext context, BoundingBox box) {
        ChunkGenerator generator = context.chunkGenerator();
        Heightmap.Types heightmap = this.projectStartToHeightmap.get();
        int[][] points = {
                {box.minX(), box.minZ()}, {box.maxX(), box.minZ()},
                {box.minX(), box.maxZ()}, {box.maxX(), box.maxZ()},
                {(box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2}};
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int[] p : points) {
            int y = generator.getFirstFreeHeight(p[0], p[1], heightmap, context.heightAccessor(), context.randomState());
            low = Math.min(low, y);
            high = Math.max(high, y);
        }
        return high - low;
    }

    /**
     * Seed, chunk and structure id hashed through Stafford mix 13 into a Xoroshiro random.
     *
     * Not setLargeFeatureWithSalt: that seed is linear (seed + x*A + z*B + salt) and feeds a
     * java.util.Random LCG, so two structures in one chunk get seeds a constant apart and their
     * first outputs come out a near-constant distance apart. In the first test world a fallen
     * tree and the dead tree in its chunk had only 30 distinct relative offsets across 91
     * chunks - the same layout repeated everywhere.
     */
    private static WorldgenRandom random(long seed, ChunkPos chunk, int salt) {
        long h = RandomSupport.mixStafford13(seed ^ 0x9E3779B97F4A7C15L);
        h = RandomSupport.mixStafford13(h ^ chunk.x * 0xC2B2AE3D27D4EB4FL);
        h = RandomSupport.mixStafford13(h ^ chunk.z * 0x165667B19E3779F9L);
        long lo = RandomSupport.mixStafford13(h ^ salt * 0x27D4EB2F165667C5L);
        long hi = RandomSupport.mixStafford13(lo ^ 0x6A09E667F3BCC909L);
        return new WorldgenRandom(new XoroshiroRandomSource(lo, hi));
    }

    @Override
    public StructureType<?> type() {
        return Frostline.PROP.get();
    }
}
