package com.momosoftworks.coldsweat.common.block;

import com.momosoftworks.coldsweat.api.registry.ThermalFuelRegistry;
import com.momosoftworks.coldsweat.common.blockentity.BoilerBlockEntity;
import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import com.momosoftworks.coldsweat.core.init.ModMenus;
import com.momosoftworks.coldsweat.core.init.ModItems;
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
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.BlockHitResult;

public class BoilerBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public BoilerBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LIT, false);
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
        int itemFuel = ThermalFuelRegistry.getBoilerFuel(stack);
        if (itemFuel <= 0)
        {
            if (stack.is(ModItems.SMOKESTACK)
                    && hit.getDirection() == Direction.UP
                    && level.getBlockState(pos.above()).canBeReplaced())
            {
                return InteractionResult.PASS;
            }

            if (player instanceof ServerPlayer serverPlayer)
            {
                ModMenus.openBoiler(serverPlayer, pos);
            }
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler
                && !level.isClientSide()
                && boiler.getFuel() <= boiler.getMaxFuel() - itemFuel)
        {
            boiler.addFuel(itemFuel);
            consumeFuelItem(player, hand, stack);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    )
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            ModMenus.openBoiler(serverPlayer, pos);
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

        if (stack.is(Items.LAVA_BUCKET))
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
    public BlockState playerWillDestroy(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player
    )
    {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler)
        {
            Containers.dropContents(level, pos, boiler);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void animateTick(
            BlockState state,
            Level level,
            BlockPos pos,
            RandomSource random
    )
    {
        if (!state.getValue(LIT) || random.nextFloat() >= 0.65F)
        {
            return;
        }

        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.2;
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

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state)
    {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(
            BlockState state,
            Level level,
            BlockPos pos,
            Direction direction
    )
    {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(
                level.getBlockEntity(pos)
        );
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new BoilerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    )
    {
        if (type != ModBlockEntities.BOILER)
        {
            return null;
        }
        return (tickLevel, pos, tickState, entity) ->
        {
            if (entity instanceof BoilerBlockEntity boiler)
            {
                BoilerBlockEntity.tick(tickLevel, pos, tickState, boiler);
            }
        };
    }
}
