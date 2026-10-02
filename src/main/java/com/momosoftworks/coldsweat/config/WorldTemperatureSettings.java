package com.momosoftworks.coldsweat.config;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fabric-side boundary for world-temperature settings.
 *
 * M4 starts with Cold Sweat's upstream defaults. File-backed configuration is
 * restored later without forcing environmental modifiers to depend directly on
 * NeoForge's config system.
 */
public final class WorldTemperatureSettings
{
    public static final long DEFAULT_HOTTEST_TIME = 6000L;
    public static final long DEFAULT_COLDEST_TIME = 18000L;

    public static final double DEFAULT_SHADE_TEMP_OFFSET =
            Temperature.convert(
                    -9.0,
                    Temperature.Units.F,
                    Temperature.Units.MC,
                    false
            );

    private static final Map<Identifier, Double> DIMENSION_TEMP_OFFSETS =
            new LinkedHashMap<>();

    private static long hottestTime = DEFAULT_HOTTEST_TIME;
    private static long coldestTime = DEFAULT_COLDEST_TIME;
    private static double shadeTempOffset = DEFAULT_SHADE_TEMP_OFFSET;

    static
    {
        resetDimensionOffsets();
    }

    public static long getHottestTime()
    {
        return hottestTime;
    }

    public static void setHottestTime(long hottestTime)
    {
        WorldTemperatureSettings.hottestTime =
                Math.floorMod(hottestTime, 24000L);
    }

    public static long getColdestTime()
    {
        return coldestTime;
    }

    public static void setColdestTime(long coldestTime)
    {
        WorldTemperatureSettings.coldestTime =
                Math.floorMod(coldestTime, 24000L);
    }

    public static double getShadeTempOffset()
    {
        return shadeTempOffset;
    }

    public static void setShadeTempOffset(double shadeTempOffset)
    {
        WorldTemperatureSettings.shadeTempOffset = shadeTempOffset;
    }

    public static double getDimensionTempOffset(Level level)
    {
        return getDimensionTempOffset(level.dimension().identifier());
    }

    public static double getDimensionTempOffset(Identifier dimensionId)
    {
        return DIMENSION_TEMP_OFFSETS.getOrDefault(dimensionId, 0.0);
    }

    public static void setDimensionTempOffset(
            Identifier dimensionId,
            double temperatureOffset
    )
    {
        DIMENSION_TEMP_OFFSETS.put(dimensionId, temperatureOffset);
    }

    public static Map<Identifier, Double> getDimensionTempOffsets()
    {
        return Map.copyOf(DIMENSION_TEMP_OFFSETS);
    }

    public static void resetToDefaults()
    {
        hottestTime = DEFAULT_HOTTEST_TIME;
        coldestTime = DEFAULT_COLDEST_TIME;
        shadeTempOffset = DEFAULT_SHADE_TEMP_OFFSET;
        resetDimensionOffsets();
    }

    private static void resetDimensionOffsets()
    {
        DIMENSION_TEMP_OFFSETS.clear();

        /*
         * Upstream world.toml defaults:
         * minecraft:the_nether = +32 F
         * minecraft:the_end    = -5 F
         *
         * These are relative offsets, so absolute=false is important.
         */
        DIMENSION_TEMP_OFFSETS.put(
                Identifier.withDefaultNamespace("the_nether"),
                Temperature.convert(
                        32.0,
                        Temperature.Units.F,
                        Temperature.Units.MC,
                        false
                )
        );
        DIMENSION_TEMP_OFFSETS.put(
                Identifier.withDefaultNamespace("the_end"),
                Temperature.convert(
                        -5.0,
                        Temperature.Units.F,
                        Temperature.Units.MC,
                        false
                )
        );
    }

    private WorldTemperatureSettings()
    {
    }
}
