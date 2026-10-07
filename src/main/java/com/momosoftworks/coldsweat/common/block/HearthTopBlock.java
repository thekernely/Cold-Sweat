package com.momosoftworks.coldsweat.common.block;

import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.core.init.ModBlocks;
import com.momosoftworks.coldsweat.core.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class HearthTopBlock extends Block
{
    public HearthTopBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (stack.is(ModItems.SMOKESTACK)
                && hit.getDirection() == Direction.UP
                && level.getBlockState(pos.above()).canBeReplaced())
        {
            return InteractionResult.PASS;
        }

        BlockPos bottomPos = pos.below();
        BlockState bottomState = level.getBlockState(bottomPos);
        if (bottomState.getBlock() instanceof HearthBottomBlock bottom)
        {
            return bottom.useItemOn(stack, bottomState, level, bottomPos, player, hand, hit);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit)
    {
        BlockPos bottomPos = pos.below();
        BlockState bottomState = level.getBlockState(bottomPos);
        if (bottomState.getBlock() instanceof HearthBottomBlock bottom)
        {
            return bottom.useWithoutItem(bottomState, level, bottomPos, player, hit);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random)
    {
        if (directionToNeighbour == Direction.DOWN && !neighbourState.is(ModBlocks.HEARTH_BOTTOM))
        {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        if (!level.isClientSide() && level.getBlockEntity(pos.below()) instanceof HearthBlockEntity hearth)
        {
            Containers.dropContents(level, pos.below(), hearth);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
