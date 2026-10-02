package com.momosoftworks.coldsweat.common.block;

import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class HearthBottomBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty COOLING = BooleanProperty.create("cooling");
    public static final BooleanProperty HEATING = BooleanProperty.create("heating");
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty FROSTED = BooleanProperty.create("frosted");
    public static final BooleanProperty SMART = BooleanProperty.create("smart");

    public HearthBottomBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(COOLING, false)
                .setValue(HEATING, false)
                .setValue(LIT, false)
                .setValue(FROSTED, false)
                .setValue(SMART, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, COOLING, HEATING, LIT, FROSTED, SMART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new HearthBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    )
    {
        if (type != ModBlockEntities.HEARTH)
        {
            return null;
        }
        return (tickLevel, pos, tickState, entity) ->
        {
            if (entity instanceof HearthBlockEntity hearth)
            {
                HearthBlockEntity.tick(tickLevel, pos, tickState, hearth);
            }
        };
    }
}
