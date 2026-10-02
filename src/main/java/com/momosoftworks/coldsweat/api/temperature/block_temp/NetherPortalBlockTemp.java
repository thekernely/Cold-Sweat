package com.momosoftworks.coldsweat.api.temperature.block_temp;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;

/**
 * Upstream Cold Sweat Nether portal temperature source.
 */
public class NetherPortalBlockTemp extends BlockTemp
{
    public NetherPortalBlockTemp()
    {
        super(Blocks.NETHER_PORTAL);
    }

    private static boolean isOverworld(Level level)
    {
        return level.dimensionTypeRegistration()
                .is(BuiltinDimensionTypes.OVERWORLD);
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
        return isOverworld(level) ? 0.3 : -0.2;
    }

    @Override
    public double getMaxEffect(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return 1.0;
    }

    @Override
    public double getMinEffect(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return -1.0;
    }

    @Override
    public double getMaxTemp(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return isOverworld(level)
                ? Double.POSITIVE_INFINITY
                : 0.0;
    }

    @Override
    public double getMinTemp(
            LivingEntity entity,
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return isOverworld(level)
                ? Double.POSITIVE_INFINITY
                : 1.0;
    }
}
