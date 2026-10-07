package com.momosoftworks.coldsweat.fabric.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Shared M9.4b weather forcing boundary.
 *
 * This deliberately uses Minecraft's Level weather/precipitation API instead
 * of directly depending on Ecliptic Seasons. When Ecliptic is installed, its
 * Level mixins already provide the locally-resolved precipitation semantics.
 *
 * The returned precipitation strength describes weather in the local biome
 * column, not whether rain/snow is physically touching the entity. Direct
 * soaking remains owned by WaterTempModifier via isRainingAt(), so shelter can
 * block wetness while a storm still exists outside the shelter.
 */
public final class WeatherExposureModel
{
    private static final LocalWeather CLEAR =
            new LocalWeather(0.0, false);

    private WeatherExposureModel()
    {
    }

    public static LocalWeather sample(
            Level level,
            BlockPos pos
    )
    {
        if (level == null
                || pos == null
                || !level.dimensionType().hasSkyLight()
                || level.dimensionType().hasCeiling())
        {
            return CLEAR;
        }

        double precipitationStrength =
                clamp01(level.getRainLevel(1.0F));

        if (precipitationStrength <= 1.0e-6)
        {
            return CLEAR;
        }

        Biome.Precipitation precipitation =
                level.precipitationAt(pos);

        if (precipitation == Biome.Precipitation.NONE)
        {
            return CLEAR;
        }

        return new LocalWeather(
                precipitationStrength,
                level.isThundering()
        );
    }

    private static double clamp01(double value)
    {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    public record LocalWeather(
            double precipitationStrength,
            boolean thundering
    )
    {
    }
}
