package com.momosoftworks.coldsweat.common.fluid;

import com.momosoftworks.coldsweat.core.init.ModBlocks;
import com.momosoftworks.coldsweat.core.init.ModFluids;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.util.registries.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.Optional;

/**
 * Fabric-native Slush fluid.
 *
 * The flow constants preserve the upstream Cold Sweat defaults:
 * slope distance 2, drop-off 2, tick delay 30 and no infinite source
 * conversion in the initial Fabric port.
 */
public abstract class SlushFluid extends FlowingFluid
{
    @Override
    public Fluid getFlowing()
    {
        return ModFluids.FLOWING_SLUSH;
    }

    @Override
    public Fluid getSource()
    {
        return ModFluids.SLUSH;
    }

    @Override
    public Item getBucket()
    {
        return ModItems.SLUSH_BUCKET;
    }

    @Override
    protected boolean canConvertToSource(ServerLevel level)
    {
        return level.getGameRules().get(
                ModGameRules.RULE_SLUSH_SOURCE_CONVERSION
        );
    }

    @Override
    protected void beforeDestroyingBlock(
            LevelAccessor level,
            BlockPos pos,
            BlockState state
    )
    {
        BlockEntity blockEntity =
                state.hasBlockEntity()
                        ? level.getBlockEntity(pos)
                        : null;
        Block.dropResources(state, level, pos, blockEntity);
    }

    @Override
    protected int getSlopeFindDistance(LevelReader level)
    {
        return 2;
    }

    @Override
    public BlockState createLegacyBlock(FluidState fluidState)
    {
        return ModBlocks.SLUSH.defaultBlockState()
                .setValue(
                        LiquidBlock.LEVEL,
                        getLegacyLevel(fluidState)
                );
    }

    @Override
    public boolean isSame(Fluid other)
    {
        return other == ModFluids.SLUSH
                || other == ModFluids.FLOWING_SLUSH;
    }

    @Override
    public int getDropOff(LevelReader level)
    {
        return 2;
    }

    @Override
    public int getTickDelay(LevelReader level)
    {
        return 30;
    }

    @Override
    public boolean canBeReplacedWith(
            FluidState state,
            BlockGetter level,
            BlockPos pos,
            Fluid other,
            Direction direction
    )
    {
        return direction == Direction.DOWN
                && !other.isSame(this);
    }

    @Override
    protected float getExplosionResistance()
    {
        return 100.0F;
    }

    @Override
    public Optional<SoundEvent> getPickupSound()
    {
        return Optional.of(SoundEvents.BUCKET_FILL_POWDER_SNOW);
    }

    public static final class Flowing extends SlushFluid
    {
        @Override
        protected void createFluidStateDefinition(
                StateDefinition.Builder<Fluid, FluidState> builder
        )
        {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state)
        {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state)
        {
            return false;
        }
    }

    public static final class Source extends SlushFluid
    {
        @Override
        public int getAmount(FluidState state)
        {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state)
        {
            return true;
        }
    }
}
