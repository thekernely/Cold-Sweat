package com.momosoftworks.coldsweat.config;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Fabric-side boundary for world-temperature settings.
 *
 * M4 starts with Cold Sweat's upstream defaults. File-backed configuration is
 * restored later without forcing environmental modifiers to depend directly on
 * NeoForge's config system.
 */
public final class WorldTemperatureSettings
{
    /*
     * M9.2b phase shift:
     * - thermal maximum trails solar noon into the afternoon
     * - thermal minimum occurs shortly before sunrise, not at midnight
     */
    public static final long DEFAULT_HOTTEST_TIME = 8000L;
    public static final long DEFAULT_COLDEST_TIME = 23000L;

    public static final double DEFAULT_SHADE_TEMP_OFFSET =
            Temperature.convert(
                    -9.0,
                    Temperature.Units.F,
                    Temperature.Units.MC,
                    false
            );

    private static final Map<Identifier, Double> DIMENSION_TEMP_OFFSETS =
            new LinkedHashMap<>();

    private static final Map<Identifier, BiomeTemperatureRange> BIOME_TEMPERATURES =
            new LinkedHashMap<>();

    private static long hottestTime = DEFAULT_HOTTEST_TIME;
    private static long coldestTime = DEFAULT_COLDEST_TIME;
    private static double shadeTempOffset = DEFAULT_SHADE_TEMP_OFFSET;

    static
    {
        resetDimensionOffsets();
        resetBiomeTemperatures();
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

    public static Optional<BiomeTemperatureRange> getBiomeTemperatureRange(
            Identifier biomeId
    )
    {
        return Optional.ofNullable(BIOME_TEMPERATURES.get(biomeId));
    }

    public static void setBiomeTemperatureRange(
            Identifier biomeId,
            double lowTemperature,
            double highTemperature
    )
    {
        BIOME_TEMPERATURES.put(
                biomeId,
                new BiomeTemperatureRange(lowTemperature, highTemperature)
        );
    }

    public static Map<Identifier, BiomeTemperatureRange> getBiomeTemperatures()
    {
        return Map.copyOf(BIOME_TEMPERATURES);
    }

    public static void resetToDefaults()
    {
        hottestTime = DEFAULT_HOTTEST_TIME;
        coldestTime = DEFAULT_COLDEST_TIME;
        shadeTempOffset = DEFAULT_SHADE_TEMP_OFFSET;
        resetDimensionOffsets();
        resetBiomeTemperatures();
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

    private static void resetBiomeTemperatures()
    {
        BIOME_TEMPERATURES.clear();

        // Cold Sweat's upstream vanilla biome defaults (absolute Fahrenheit).
        addBiomeF("badlands", 84, 120);
        addBiomeF("bamboo_jungle", 76, 87);
        addBiomeF("beach", 52, 84);
        addBiomeF("birch_forest", 47, 77);
        addBiomeF("cherry_grove", 42, 67);
        addBiomeF("cold_ocean", 39, 70);
        addBiomeF("dark_forest", 53, 80);
        addBiomeF("deep_cold_ocean", 39, 70);
        addBiomeF("deep_dark", 63, 63);
        addBiomeF("deep_frozen_ocean", 8, 31);
        addBiomeF("deep_lukewarm_ocean", 39, 70);
        addBiomeF("deep_ocean", 39, 70);
        addBiomeF("desert", 48, 115);
        // minecraft:dripstone_caves is explicitly "disable" upstream.
        addBiomeF("eroded_badlands", 88, 120);
        addBiomeF("flower_forest", 51, 76);
        addBiomeF("forest", 51, 76);
        addBiomeF("frozen_ocean", 15, 31);
        addBiomeF("frozen_peaks", 8, 31);
        addBiomeF("frozen_river", 15, 31);
        addBiomeF("grove", 10, 36);
        addBiomeF("ice_spikes", 17, 47);
        addBiomeF("jagged_peaks", -11, 12);
        addBiomeF("jungle", 76, 89);
        addBiomeF("lukewarm_ocean", 39, 70);
        addBiomeF("lush_caves", 39, 70);
        addBiomeF("mangrove_swamp", 56, 80);
        addBiomeF("meadow", 42, 67);
        addBiomeF("mushroom_fields", 61, 84);
        addBiomeF("ocean", 39, 70);
        addBiomeF("old_growth_birch_forest", 58, 72);
        addBiomeF("old_growth_pine_taiga", 48, 62);
        addBiomeF("old_growth_spruce_taiga", 48, 62);
        addBiomeF("plains", 52, 84);
        // minecraft:river is explicitly "disable" upstream.
        addBiomeF("savanna", 70, 95);
        addBiomeF("savanna_plateau", 76, 98);
        addBiomeF("small_end_islands", 39, 70);
        addBiomeF("snowy_beach", 8, 30);
        addBiomeF("snowy_plains", 8, 30);
        addBiomeF("snowy_slopes", 24, 32);
        addBiomeF("snowy_taiga", 8, 30);
        addBiomeF("soul_sand_valley", 53, 53);
        addBiomeF("sparse_jungle", 62, 87);
        addBiomeF("stony_peaks", 60, 94);
        addBiomeF("stony_shore", 50, 64);
        addBiomeF("sunflower_plains", 52, 84);
        addBiomeF("swamp", 72, 84);
        addBiomeF("taiga", 44, 62);
        addBiomeF("warm_ocean", 67, 76);
        addBiomeF("windswept_forest", 48, 66);
        addBiomeF("windswept_gravelly_hills", 24, 58);
        addBiomeF("windswept_hills", 24, 58);
        addBiomeF("windswept_savanna", 67, 90);
        addBiomeF("wooded_badlands", 80, 108);
    }

    private static void addBiomeF(
            String path,
            double lowFahrenheit,
            double highFahrenheit
    )
    {
        setBiomeTemperatureRange(
                Identifier.withDefaultNamespace(path),
                Temperature.convert(
                        lowFahrenheit,
                        Temperature.Units.F,
                        Temperature.Units.MC,
                        true
                ),
                Temperature.convert(
                        highFahrenheit,
                        Temperature.Units.F,
                        Temperature.Units.MC,
                        true
                )
        );
    }

    public record BiomeTemperatureRange(
            double lowTemperature,
            double highTemperature
    )
    {
        public double sample(double timeMultiplier)
        {
            double clamped =
                    Math.max(-1.0, Math.min(1.0, timeMultiplier));

            double progress = (clamped + 1.0) / 2.0;

            return lowTemperature
                    + (highTemperature - lowTemperature) * progress;
        }
    }

    private WorldTemperatureSettings()
    {
    }
}
