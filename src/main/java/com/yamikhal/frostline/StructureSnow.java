package com.yamikhal.frostline;

import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * Where structure templates put minecraft:snow in a chunk: the positions every snow pass must
 * leave alone (NotStructureSnowFilter for placed passes, FreezeTopLayerFeature for the one
 * vanilla pass that has no placement to filter).
 *
 * Positions come from each single-pool-element piece's template, rotated like the piece, raw
 * (before processors), for the pieces the chunk's structure references name. One set per chunk,
 * cached per thread for the chunk being decorated: the snow passes query it thousands of times.
 */
final class StructureSnow {

    /** SinglePoolElement.template: protected, and no public getter returns the template. */
    private static final Field TEMPLATE = ObfuscationReflectionHelper.findField(SinglePoolElement.class, "f_210411_");

    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

    private static final class Cache {
        WorldGenRegion region;
        long chunk;
        LongSet positions;
    }

    private StructureSnow() {
    }

    /** Template snow positions in the given chunk, as BlockPos longs. */
    static LongSet in(WorldGenRegion region, ChunkPos chunk) {
        Cache cache = CACHE.get();
        long key = chunk.toLong();
        if (cache.region != region || cache.chunk != key || cache.positions == null) {
            cache.region = region;
            cache.chunk = key;
            cache.positions = compute(region, chunk);
        }
        return cache.positions;
    }

    private static LongSet compute(WorldGenRegion region, ChunkPos chunk) {
        LongSet out = new LongOpenHashSet();
        StructureManager structures = region.getLevel().structureManager().forWorldGenRegion(region);
        StructureTemplateManager templates = region.getLevel().getStructureManager();
        BoundingBox chunkBox = new BoundingBox(chunk.getMinBlockX(), region.getMinBuildHeight(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), region.getMaxBuildHeight(), chunk.getMaxBlockZ());

        for (StructureStart start : structures.startsForStructure(chunk, s -> true)) {
            for (StructurePiece piece : start.getPieces()) {
                if (!(piece instanceof PoolElementStructurePiece pool)
                        || !(pool.getElement() instanceof SinglePoolElement element)
                        || !piece.getBoundingBox().intersects(chunkBox)) {
                    continue;
                }
                StructureTemplate template = template(element, templates);
                if (template == null) {
                    continue;
                }
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(pool.getRotation());
                for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(pool.getPosition(), settings, Blocks.SNOW)) {
                    if (chunkBox.isInside(info.pos())) {
                        out.add(info.pos().asLong());
                    }
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static StructureTemplate template(SinglePoolElement element, StructureTemplateManager templates) {
        Either<ResourceLocation, StructureTemplate> either;
        try {
            either = (Either<ResourceLocation, StructureTemplate>) TEMPLATE.get(element);
        } catch (IllegalAccessException e) {
            return null;
        }
        return either.map(templates::getOrCreate, t -> t);
    }
}
