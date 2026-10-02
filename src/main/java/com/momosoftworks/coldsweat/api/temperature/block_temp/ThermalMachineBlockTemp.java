package com.momosoftworks.coldsweat.api.temperature.block_temp;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * State-aware static block temperature for the M6 thermal machines.
 */
public final class ThermalMachineBlockTemp extends SimpleBlockTemp
{
    private final double temperature;
    private final Predicate<BlockState> activePredicate;

    public ThermalMachineBlockTemp(
            Block block,
            double temperatureF,
            double range,
            double maxEffectF,
            double temperatureLimitF,
            Predicate<BlockState> activePredicate
    )
    {
        super(
                -Math.abs(relativeF(maxEffectF)),
                Math.abs(relativeF(maxEffectF)),
                temperatureF < 0
                        ? absoluteF(temperatureLimitF)
                        : Double.NEGATIVE_INFINITY,
                temperatureF > 0
                        ? absoluteF(temperatureLimitF)
                        : Double.POSITIVE_INFINITY,
                range,
                true,
                false,
                block
        );

        this.temperature = relativeF(temperatureF);
        this.activePredicate = activePredicate;
    }

    @Override
    public boolean isValid(
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return matches(state) && activePredicate.test(state);
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
        return temperature;
    }

    private static double relativeF(double value)
    {
        return Temperature.convert(
                value,
                Temperature.Units.F,
                Temperature.Units.MC,
                false
        );
    }

    private static double absoluteF(double value)
    {
        return Temperature.convert(
                value,
                Temperature.Units.F,
                Temperature.Units.MC,
                true
        );
    }
}
