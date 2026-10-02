package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Vanilla freezing exposure applied to Cold Sweat's BASE trait.
 *
 * Mirrors upstream FreezingTempModifier: vanilla frozen ticks blend from
 * 0 to 20 BASE temperature points, then the modifier expires once thawed.
 */
public class FreezingTempModifier extends TempModifier
{
    @Override
    protected Function<Double, Double> calculate(
            LivingEntity entity,
            Temperature.Trait trait
    )
    {
        int requiredTicks =
                Math.max(
                        1,
                        entity.getTicksRequiredToFreeze()
                );

        double progress =
                Math.max(
                        0.0,
                        Math.min(
                                1.0,
                                entity.getTicksFrozen()
                                        / (double) requiredTicks
                        )
                );

        double freezeAmount = 20.0 * progress;

        if (Double.compare(freezeAmount, 0.0) == 0)
        {
            expires(0);
        }

        return temperature ->
                temperature - freezeAmount;
    }
}
