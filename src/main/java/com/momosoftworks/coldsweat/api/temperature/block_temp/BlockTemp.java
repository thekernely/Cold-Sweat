package com.momosoftworks.coldsweat.api.temperature.block_temp;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Loader-independent block-temperature source definition.
 */
public abstract class BlockTemp
{
    public static final double DEFAULT_RANGE = 7.0;

    private final Set<Block> validBlocks;

    protected BlockTemp(Block... blocks)
    {
        LinkedHashSet<Block> copy = new LinkedHashSet<>();
        copy.addAll(Arrays.asList(blocks));
        validBlocks = Collections.unmodifiableSet(copy);
    }

    public abstract double getTemperature(
            Level level,
            LivingEntity entity,
            BlockState state,
            BlockPos pos,
            double distance
    );

    public boolean isValid(Level level, BlockPos pos, BlockState state)
    {
        return true;
    }

    public boolean hasBlock(Block block)
    {
        return validBlocks.contains(block);
    }

    /**
     * State-aware matching hook. Upstream config-backed BlockTemps can target
     * block tags; keeping this separate from hasBlock(...) lets static Java
     * sources keep their fast block matching while tag-driven defaults remain
     * exact.
     */
    public boolean matches(BlockState state)
    {
        return hasBlock(state.getBlock());
    }

    public Set<Block> getAffectedBlocks()
    {
        return validBlocks;
    }

    public double getMaxEffect(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return Double.POSITIVE_INFINITY;
    }

    public double getMinEffect(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return Double.NEGATIVE_INFINITY;
    }

    public double getMaxTemp(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return Double.POSITIVE_INFINITY;
    }

    public double getMinTemp(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return Double.NEGATIVE_INFINITY;
    }

    public double getRange(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return DEFAULT_RANGE;
    }

    public boolean isLogarithmic(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return false;
    }

    public boolean fades(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return true;
    }
}
