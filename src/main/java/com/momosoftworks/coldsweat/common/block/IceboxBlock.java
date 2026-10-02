package com.momosoftworks.coldsweat.common.block;

import com.momosoftworks.coldsweat.common.blockentity.IceboxBlockEntity;
import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class IceboxBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FROSTED = BooleanProperty.create("frosted");
    public static final BooleanProperty SMOKESTACK = BooleanProperty.create("smokestack");

    public IceboxBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(FROSTED, false)
                .setValue(SMOKESTACK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, FROSTED, SMOKESTACK);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(FROSTED, false)
                .setValue(SMOKESTACK, false);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new IceboxBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    )
    {
        if (type != ModBlockEntities.ICEBOX)
        {
            return null;
        }
        return (tickLevel, pos, tickState, entity) ->
        {
            if (entity instanceof IceboxBlockEntity icebox)
            {
                IceboxBlockEntity.tick(tickLevel, pos, tickState, icebox);
            }
        };
    }
}
