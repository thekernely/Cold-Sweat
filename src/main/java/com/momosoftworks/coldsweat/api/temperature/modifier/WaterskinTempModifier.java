package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Gradual CORE-temperature effect applied after drinking a filled waterskin.
 */
public final class WaterskinTempModifier extends TempModifier
{
    private final double temperature;

    public WaterskinTempModifier()
    {
        this(0.0);
    }

    public WaterskinTempModifier(double temperature)
    {
        this.temperature = temperature;
    }

    public double getTemperature()
    {
        return temperature;
    }

    @Override
    protected Function<Double, Double> calculate(
            LivingEntity entity,
            Temperature.Trait trait
    )
    {
        return value -> value + temperature;
    }
}
