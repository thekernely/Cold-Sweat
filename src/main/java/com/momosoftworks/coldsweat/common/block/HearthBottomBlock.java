package com.momosoftworks.coldsweat.common.block;

import com.momosoftworks.coldsweat.api.registry.ThermalFuelRegistry;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import com.momosoftworks.coldsweat.core.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

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
        BlockPos topPos = context.getClickedPos().above();
        if (!context.getLevel().getBlockState(topPos).canBeReplaced())
        {
            return null;
        }

        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack stack
    )
    {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide())
        {
            level.setBlock(
                    pos.above(),
                    ModBlocks.HEARTH_TOP.defaultBlockState(),
                    3
            );
        }
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    )
    {
        int itemFuel = ThermalFuelRegistry.getHearthFuel(stack);
        if (itemFuel == 0)
        {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (level.getBlockEntity(pos) instanceof HearthBlockEntity hearth)
        {
            int magnitude = Math.abs(itemFuel);
            int stored = itemFuel > 0
                    ? hearth.getHotFuel()
                    : hearth.getColdFuel();

            if (!level.isClientSide()
                    && stored <= hearth.getMaxFuel() - magnitude)
            {
                if (itemFuel > 0)
                {
                    hearth.addHotFuel(magnitude);
                }
                else
                {
                    hearth.addColdFuel(magnitude);
                }

                consumeFuelItem(player, hand, stack);
            }
        }

        return InteractionResult.SUCCESS;
    }

    private static void consumeFuelItem(
            Player player,
            InteractionHand hand,
            ItemStack stack
    )
    {
        if (player.isCreative())
        {
            return;
        }

        if (stack.is(Items.LAVA_BUCKET)
                || stack.is(Items.POWDER_SNOW_BUCKET))
        {
            player.setItemInHand(
                    hand,
                    new ItemStack(Items.BUCKET)
            );
        }
        else
        {
            stack.shrink(1);
        }
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction directionToNeighbour,
            BlockPos neighbourPos,
            BlockState neighbourState,
            RandomSource random
    )
    {
        if (directionToNeighbour == Direction.UP
                && !neighbourState.is(ModBlocks.HEARTH_TOP))
        {
            return Blocks.AIR.defaultBlockState();
        }

        return super.updateShape(
                state,
                level,
                ticks,
                pos,
                directionToNeighbour,
                neighbourPos,
                neighbourState,
                random
        );
    }

    @Override
    public BlockState playerWillDestroy(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player
    )
    {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof HearthBlockEntity hearth)
        {
            Containers.dropContents(level, pos, hearth);
        }

        return super.playerWillDestroy(level, pos, state, player);
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
