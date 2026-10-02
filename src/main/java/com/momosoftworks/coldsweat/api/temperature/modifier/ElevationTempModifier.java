package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.function.Function;

/**
 * First Fabric elevation-temperature modifier.
 *
 * Full Cold Sweat depth-region configuration is restored later in M4. This
 * foundation mirrors Minecraft's vanilla high-altitude cooling threshold and
 * deliberately does nothing in roofed dimensions.
 */
public class ElevationTempModifier extends TempModifier
{
    private final int samples;

    public ElevationTempModifier()
    {
        this(49);
    }

    public ElevationTempModifier(int samples)
    {
        this.samples = Math.max(1, samples);
    }

    @Override
    protected Function<Double, Double> calculate(
            LivingEntity entity,
            Temperature.Trait trait
    )
    {
        Level level = entity.level();

        if (level.dimensionType().hasCeiling())
        {
            return temperature -> temperature;
        }

        int snowLevel = level.getSeaLevel() + 17;
        int y = entity.blockPosition().getY();

        if (y <= snowLevel)
        {
            return temperature -> temperature;
        }

        // Minecraft's biome temperature uses a 0.05 / 40 cooling slope above
        // sea level + 17. We preserve that deterministic altitude component;
        // vanilla's private positional noise term is intentionally excluded.
        double altitudeOffset = -(y - snowLevel) * 0.05 / 40.0;

        return temperature -> temperature + altitudeOffset;
    }

    public int getSamples()
    {
        return samples;
    }
}
