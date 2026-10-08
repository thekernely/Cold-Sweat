package com.momosoftworks.coldsweat.fabric.ecology;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Generic, purely decorative frost-killed crop. No item, drops or seeds.
 * The first slice targets one-block farmland crops only.
 */
public final class WitheredCropBlock extends BushBlock
{
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 11.0, 14.0);

    public WitheredCropBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
                                  BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState ground, BlockGetter level, BlockPos pos)
    {
        return ground.is(Blocks.FARMLAND)
                || ground.is(BlockTags.SUPPORTS_CROPS)
                || super.mayPlaceOn(ground, level, pos);
    }
}
