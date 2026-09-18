package com.yamikhal.frostline;

import com.yamikhal.Frostline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Ice that gives way underfoot. Walk over it and it cracks a stage at a time; land on it hard
 * and it may go straight through to open water.
 *
 * This replaces immersive_weathering:thin_ice, which the pack used until that mod was dropped.
 * The behaviour is deliberately the same one, read out of ThinIceBlock: the same four crack
 * stages, the same 1-in-15 step roll, the same fall-distance roll, the same crack spreading to
 * neighbours on impact, the same two sounds. What is NOT carried over is the shape: thin_ice was
 * a 4-pixel slab, this is a full cube.
 *
 * Stage 0 renders as vanilla block/ice and ships no model or texture, so undamaged cracked ice is
 * indistinguishable from the real ice it sits in - you find out it was fragile by standing on it.
 * Stages 1-3 each have a cube_all model over a crack texture. Those textures are vanilla ice.png
 * with only the crack pixels of thin_ice_1..3 composited on, so a cracking block still matches the
 * sheet around it instead of switching to a second, slightly different blue.
 *
 * Melting, silk touch and the water drop below are IceBlock's, untouched.
 */
public class CrackedIceBlock extends IceBlock {

    public static final IntegerProperty CRACKED = IntegerProperty.create("cracked", 0, 3);

    /** width^2 * height. Under this an entity is too light to crack anything: a rabbit walks over. */
    private static final float MIN_BULK = 0.512F;
    /** One step in fifteen is what rolls a crack, so a crossing is usually survivable. */
    private static final int STEP_CHANCE = 15;

    public CrackedIceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(CRACKED, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CRACKED);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!skates(entity) && !level.isClientSide && heavyEnough(level, entity)
                && level.random.nextInt(STEP_CHANCE) == 0) {
            crackOrBreak(level, pos, state);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        // ThinIceBlock spread its cracks on both sides; doing that on the client is a write the
        // server never agreed to, so the whole impact is server side here.
        if (!skates(entity) && !level.isClientSide) {
            if (level.random.nextFloat() < fallDistance - 0.5F && heavyEnough(level, entity)) {
                // A hard landing gets a coin flip to punch straight through whatever the stage was.
                if (level.random.nextBoolean()) {
                    breakThrough(level, pos);
                } else {
                    crackOrBreak(level, pos, state);
                }
            }
            spreadCracks(level, pos);
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    /** Frost Walker freezes the water it stands on; it has no business breaking ice. */
    private static boolean skates(Entity entity) {
        return entity instanceof LivingEntity living
                && EnchantmentHelper.getEnchantmentLevel(Enchantments.FROST_WALKER, living) > 0;
    }

    private static boolean heavyEnough(Level level, Entity entity) {
        if (!(entity instanceof Player) && !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        return entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() > MIN_BULK;
    }

    private void crackOrBreak(Level level, BlockPos pos, BlockState state) {
        int cracked = state.getValue(CRACKED);
        if (cracked < 3) {
            level.setBlockAndUpdate(pos, state.setValue(CRACKED, cracked + 1));
            level.playSound(null, pos, Frostline.ICE_CRACK.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            breakThrough(level, pos);
        }
    }

    private static void breakThrough(Level level, BlockPos pos) {
        level.setBlockAndUpdate(pos, updateFromNeighbourShapes(Blocks.WATER.defaultBlockState(), level, pos));
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** An impact runs a crack outward, so a sheet fails as a sheet rather than one block at a time. */
    private void spreadCracks(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos side = pos.relative(direction);
            BlockState neighbour = level.getBlockState(side);
            if (!neighbour.is(this)) {
                continue;
            }
            int cracked = neighbour.getValue(CRACKED);
            if (cracked < 3 && level.random.nextBoolean()) {
                level.setBlockAndUpdate(side, neighbour.setValue(CRACKED, cracked + 1));
            }
        }
    }
}
