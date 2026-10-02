package com.momosoftworks.coldsweat.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fabric 26.2 smokestack transfer medium.
 *
 * M6.5 restores the thermal-routing role first. The richer upstream connection
 * visuals/encasing behavior can layer on top without changing the routing
 * contract exposed here.
 */
public class SmokestackBlock extends Block
{
    public static final EnumProperty<Direction> FACING =
            EnumProperty.create("facing", Direction.class);

    public SmokestackBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
        registerDefaultState(
                defaultBlockState()
                        .setValue(FACING, Direction.UP)
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace());
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    )
    {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    )
    {
        return switch (state.getValue(FACING).getAxis())
        {
            case X -> Block.box(0, 4, 4, 16, 12, 12);
            case Z -> Block.box(4, 4, 0, 12, 12, 16);
            case Y -> Block.box(4, 0, 4, 12, 16, 12);
        };
    }

    public static boolean allowsDirection(
            BlockState state,
            Direction direction
    )
    {
        return state.getValue(FACING).getAxis()
                == direction.getAxis();
    }
}
