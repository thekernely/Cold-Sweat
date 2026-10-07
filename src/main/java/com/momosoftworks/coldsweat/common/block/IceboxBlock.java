package com.momosoftworks.coldsweat.common.block;

import com.momosoftworks.coldsweat.common.blockentity.IceboxBlockEntity;
import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.core.init.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
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
                .setValue(SMOKESTACK,
                        context.getLevel().getBlockState(context.getClickedPos().above()).getBlock()
                                instanceof SmokestackBlock);
    }

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

        if (player instanceof ServerPlayer serverPlayer)
        {
            ModMenus.openIcebox(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            ModMenus.openIcebox(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random)
    {
        if (directionToNeighbour == Direction.UP)
        {
            state = state.setValue(SMOKESTACK, neighbourState.getBlock() instanceof SmokestackBlock);
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (!state.getValue(FROSTED) || random.nextFloat() >= 0.4F) return;
        double x = pos.getX() + 0.5 + random.nextDouble() - 0.5;
        double y = pos.getY() + 0.08 + random.nextDouble() * 0.25;
        double z = pos.getZ() + 0.5 + random.nextDouble() - 0.5;
        level.addParticle(ParticleTypes.CLOUD, x, y, z, 0, 0.005, 0);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) { return true; }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction)
    {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof IceboxBlockEntity icebox)
        {
            Containers.dropContents(level, pos, icebox);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new IceboxBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (type != ModBlockEntities.ICEBOX) return null;
        return (tickLevel, pos, tickState, entity) ->
        {
            if (entity instanceof IceboxBlockEntity icebox)
            {
                IceboxBlockEntity.tick(tickLevel, pos, tickState, icebox);
            }
        };
    }
}
