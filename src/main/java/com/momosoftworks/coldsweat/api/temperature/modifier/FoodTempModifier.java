package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

import java.util.function.Function;

/**
 * Temperature effect created by consuming an item.
 *
 * Upstream stores the same state in TempModifier NBT. The Fabric 26.2 port
 * keeps the runtime fields directly until the general modifier codec layer is
 * needed, preserving the temperature/duration/duplicate behavior without
 * dragging the configuration graph into M5.
 */
public class FoodTempModifier extends TempModifier
{
    private final Item source;
    private final double temperature;

    public FoodTempModifier(Item source, double temperature)
    {
        this.source = source;
        this.temperature = temperature;
    }

    public Item getSource()
    {
        return source;
    }

    public double getTemperature()
    {
        return temperature;
    }

    public boolean matches(Item item, double effect)
    {
        return source == item && Double.compare(temperature, effect) == 0;
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
