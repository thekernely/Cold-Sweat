package com.momosoftworks.coldsweat.api.temperature.block_temp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Upstream Cold Sweat furnace heat source.
 *
 * Applies to furnaces, blast furnaces, smokers, and any registered block type
 * derived from AbstractFurnaceBlock.
 */
public class FurnaceBlockTemp extends BlockTemp
{
    public FurnaceBlockTemp()
    {
        super(
                BuiltInRegistries.BLOCK.stream()
                        .filter(block -> block instanceof AbstractFurnaceBlock)
                        .toArray(Block[]::new)
        );
    }

    @Override
    public double getTemperature(
            Level level,
            LivingEntity entity,
            BlockState state,
            BlockPos pos,
            double distance
    )
    {
        return 0.33;
    }

    @Override
    public double getMinEffect(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return 0.0;
    }

    @Override
    public double getMaxEffect(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return 0.88;
    }

    @Override
    public double getMinTemp(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public double getMaxTemp(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return 12.6;
    }

    @Override
    public boolean hasBlock(Block block)
    {
        return block instanceof AbstractFurnaceBlock;
    }

    @Override
    public boolean isLogarithmic(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return true;
    }

    @Override
    public boolean isValid(
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return state.hasProperty(AbstractFurnaceBlock.LIT)
                && state.getValue(AbstractFurnaceBlock.LIT);
    }
}
