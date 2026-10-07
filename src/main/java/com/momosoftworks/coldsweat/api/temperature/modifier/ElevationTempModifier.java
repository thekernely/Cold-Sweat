package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.core.BlockPos;
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
        double altitudeOffset =
                getAltitudeOffset(
                        entity.level(),
                        entity.blockPosition()
                );

        return temperature -> temperature + altitudeOffset;
    }

    /**
     * Point-local elevation contribution shared by entity and ecology climate
     * sampling. The return value is a relative Minecraft-temperature delta.
     */
    public static double getAltitudeOffset(
            Level level,
            BlockPos pos
    )
    {
        if (level.dimensionType().hasCeiling())
        {
            return 0.0;
        }

        int snowLevel = level.getSeaLevel() + 17;
        int y = pos.getY();

        if (y <= snowLevel)
        {
            return 0.0;
        }

        // Minecraft's biome temperature uses a 0.05 / 40 cooling slope above
        // sea level + 17. We preserve that deterministic altitude component;
        // vanilla's private positional noise term is intentionally excluded.
        return -(y - snowLevel) * 0.05 / 40.0;
    }

    public int getSamples()
    {
        return samples;
    }
}
