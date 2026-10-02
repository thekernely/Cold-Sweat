package com.momosoftworks.coldsweat.api.temperature.block_temp;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A BlockTemp whose limits/range behavior are entirely static.
 *
 * This is the loader-independent upstream Cold Sweat abstraction.
 */
public abstract class SimpleBlockTemp extends BlockTemp
{
    protected final double minEffect;
    protected final double maxEffect;
    protected final double minTemp;
    protected final double maxTemp;
    protected final double range;
    protected final boolean fade;
    protected final boolean logarithmic;

    protected SimpleBlockTemp(
            double minEffect,
            double maxEffect,
            double minTemp,
            double maxTemp,
            double range,
            boolean fade,
            boolean logarithmic,
            Block... blocks
    )
    {
        super(blocks);
        this.minEffect = minEffect;
        this.maxEffect = maxEffect;
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
        this.range = range;
        this.fade = fade;
        this.logarithmic = logarithmic;
    }

    @Override
    public double getMinEffect(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return minEffect;
    }

    @Override
    public double getMaxEffect(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return maxEffect;
    }

    @Override
    public double getMinTemp(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return minTemp;
    }

    @Override
    public double getMaxTemp(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return maxTemp;
    }

    @Override
    public double getRange(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return range;
    }

    @Override
    public boolean fades(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return fade;
    }

    @Override
    public boolean isLogarithmic(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return logarithmic;
    }
}
