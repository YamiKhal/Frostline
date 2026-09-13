package com.yamikhal.frostline.mixin.railways;

import com.vodmordia.railwaysuntold.worldgen.head.InitialChunkSelector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The first line always runs north-south. Railways Untold flips a seeded coin between a
 * north-south and an east-west line; Frostline's journey is along z.
 *
 * The start chunk moves too: Railways Untold offsets it one chunk beside spawn,
 * perpendicular to the track, so the starting train never runs over spawn. For a
 * north-south line that offset has to be east or west, keeping its sign.
 */
@Mixin(value = InitialChunkSelector.class, remap = false)
public abstract class InitialChunkSelectorMixin {

    @Shadow
    @Final
    private BlockPos worldSpawn;

    @Inject(method = "getInitialDirection", at = @At("RETURN"), cancellable = true)
    private void frostline$alwaysNorthSouth(CallbackInfoReturnable<Direction> cir) {
        cir.setReturnValue(Direction.NORTH);
    }

    @Inject(method = "getInitialChunk", at = @At("RETURN"), cancellable = true)
    private void frostline$beside(CallbackInfoReturnable<ChunkPos> cir) {
        ChunkPos picked = cir.getReturnValue();
        ChunkPos spawn = new ChunkPos(worldSpawn);
        int offset = picked.z - spawn.z;
        if (offset != 0) {
            cir.setReturnValue(new ChunkPos(spawn.x + offset, spawn.z));
        }
    }
}
