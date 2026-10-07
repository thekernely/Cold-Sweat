package com.momosoftworks.coldsweat.common.block;

import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import com.momosoftworks.coldsweat.core.init.ModBlocks;
import com.momosoftworks.coldsweat.core.init.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
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
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockPos topPos = context.getClickedPos().above();
        if (!context.getLevel().getBlockState(topPos).canBeReplaced())
        {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide())
        {
            level.setBlock(pos.above(), ModBlocks.HEARTH_TOP.defaultBlockState(), 3);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            ModMenus.openHearth(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            ModMenus.openHearth(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random)
    {
        if (directionToNeighbour == Direction.UP && !neighbourState.is(ModBlocks.HEARTH_TOP))
        {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HearthBlockEntity hearth)
        {
            Containers.dropContents(level, pos, hearth);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (state.getValue(LIT) && random.nextFloat() < 0.6F)
        {
            spawnHotParticles(level, pos, state, random);
        }
        if (state.getValue(FROSTED) && random.nextFloat() < 0.35F)
        {
            spawnColdMist(level, pos, random);
        }
    }

    private static void spawnHotParticles(Level level, BlockPos pos, BlockState state, RandomSource random)
    {
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.35 + random.nextDouble() * 0.2;
        double z = pos.getZ() + 0.5;
        double side = random.nextDouble() * 0.5 - 0.25;

        if (facing.getAxis() == Direction.Axis.X)
        {
            x += facing.getStepX() * 0.52;
            z += side;
        }
        else
        {
            z += facing.getStepZ() * 0.52;
            x += side;
        }

        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
    }

    private static void spawnColdMist(Level level, BlockPos pos, RandomSource random)
    {
        double x = pos.getX() + 0.5 + random.nextDouble() - 0.5;
        double y = pos.getY() + 0.08 + random.nextDouble() * 0.22;
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new HearthBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (type != ModBlockEntities.HEARTH) return null;
        return (tickLevel, pos, tickState, entity) ->
        {
            if (entity instanceof HearthBlockEntity hearth)
            {
                HearthBlockEntity.tick(tickLevel, pos, tickState, hearth);
            }
        };
    }
}
