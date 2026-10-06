package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.WorldTemperatureSettings;
import com.momosoftworks.coldsweat.fabric.season.SeasonContextService;
import com.momosoftworks.coldsweat.util.world.WorldTemperatureUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;
import java.util.function.Function;

/**
 * Fabric biome-temperature modifier.
 *
 * M7.12e deliberately moves away from a tight local sample. Homeostatic's
 * environment model averages a 7x7 area at one-chunk spacing (three chunks in
 * every horizontal direction) before body-temperature calculations. We adopt
 * the same broad-climate idea here while retaining Cold Sweat's own configured
 * biome ranges, time-of-day model, dimension offsets, and modifier API.
 *
 * M9.2a layered optional Ecliptic Seasons climate onto each biome sample.
 * M9.2b translates Ecliptic's normalized cold-season signal into a Cold Sweat
 * seasonal envelope instead of treating the raw biome delta as Celsius or
 * multiplying it by one global factor.
 *
 * A single biome boundary can therefore only replace a small fraction of the
 * climate sample instead of changing the player's apparent surroundings by
 * several degrees at once.
 */
public class BiomeTempModifier extends TempModifier
{
    private static final int SAMPLE_SPACING_BLOCKS = 16;

    private static final double DEEP_WINTER_TEMPERATE_DROP_C = 29.0;
    private static final double DEEP_WINTER_COLD_DROP_C = 12.0;
    private static final double DEEP_WINTER_HOT_DROP_C = 15.0;

    private static final double WINTER_SPAN_MIN_C = 6.0;
    private static final double WINTER_SPAN_MAX_C = 12.0;
    private static final double WINTER_SPAN_SCALE = 0.40;

    private final int samples;

    public BiomeTempModifier()
    {
        // 7 x 7 = three chunks in every horizontal direction.
        this(49);
    }

    public BiomeTempModifier(int samples)
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
        BlockPos center = entity.blockPosition();

        int side = (int) Math.ceil(Math.sqrt(samples));
        int half = side / 2;
        int collected = 0;
        double total = 0.0;

        double timeMultiplier =
                WorldTemperatureUtil.getTimeMultiplier(level);

        /*
         * Keep the kernel centered on the player's real position rather than
         * snapping it to a chunk. Individual biome samples may change while
         * moving, but each contributes only 1/49 of the default climate value.
         * The runtime's ambient inertia then smooths the remaining small step.
         */
        for (int x = 0; x < side && collected < samples; x++)
        {
            for (int z = 0; z < side && collected < samples; z++)
            {
                int xOffset =
                        (x - half) * SAMPLE_SPACING_BLOCKS;
                int zOffset =
                        (z - half) * SAMPLE_SPACING_BLOCKS;

                BlockPos samplePos = new BlockPos(
                        center.getX() + xOffset,
                        center.getY(),
                        center.getZ() + zOffset
                );

                Holder<Biome> biome =
                        level.getBiome(samplePos);

                total += getBiomeTemperature(
                        level,
                        biome,
                        timeMultiplier
                );
                collected++;
            }
        }

        double sampledTemperature =
                collected > 0
                        ? total / collected
                        : getBiomeTemperature(
                                level,
                                level.getBiome(center),
                                timeMultiplier
                        );

        double dimensionOffset =
                WorldTemperatureSettings.getDimensionTempOffset(level);

        return temperature ->
                temperature
                        + sampledTemperature
                        + dimensionOffset;
    }

    private static double getBiomeTemperature(
            Level level,
            Holder<Biome> biome,
            double timeMultiplier
    )
    {
        Optional<Identifier> biomeId =
                biome.unwrapKey()
                        .map(ResourceKey::identifier);

        SeasonContextService.BiomeClimateSample seasonalClimate =
                SeasonContextService.getBiomeClimateSample(
                        level,
                        biome.value()
                );

        if (biomeId.isPresent())
        {
            Optional<WorldTemperatureSettings.BiomeTemperatureRange> range =
                    WorldTemperatureSettings.getBiomeTemperatureRange(
                            biomeId.get()
                    );

            if (range.isPresent())
            {
                return sampleSeasonalRange(
                        range.get(),
                        timeMultiplier,
                        seasonalClimate
                );
            }
        }

        /*
         * Unknown/modded biomes retain the M9.2a fallback for now: vanilla
         * biome temperature plus Ecliptic's real resolved seasonal delta.
         * A generic inferred daily range for unconfigured biomes belongs in
         * the dedicated modded-biome fallback slice, not this winter tuning
         * change.
         */
        return biome.value().getBaseTemperature()
                + seasonalClimate.offset();
    }

    private static double sampleSeasonalRange(
            WorldTemperatureSettings.BiomeTemperatureRange range,
            double timeMultiplier,
            SeasonContextService.BiomeClimateSample seasonalClimate
    )
    {
        double coldIntensity =
                clamp(seasonalClimate.coldIntensity(), 0.0, 1.25);

        if (coldIntensity <= 0.0)
        {
            return range.sample(timeMultiplier)
                    + seasonalClimate.offset();
        }

        WinterRange deepWinter =
                deriveDeepWinterRange(
                        range.lowTemperature(),
                        range.highTemperature()
                );

        double seasonalLow =
                lerp(
                        range.lowTemperature(),
                        deepWinter.lowTemperature(),
                        coldIntensity
                );

        double seasonalHigh =
                lerp(
                        range.highTemperature(),
                        deepWinter.highTemperature(),
                        coldIntensity
                );

        return sampleRange(
                seasonalLow,
                seasonalHigh,
                timeMultiplier
        );
    }

    /**
     * Translate a Cold Sweat annual biome range into the deep-winter envelope
     * used when Ecliptic reports maximum cold-season intensity.
     *
     * <p>The response is intentionally biome-relative:
     *
     * <ul>
     *     <li>Temperate climates receive the strongest seasonal depression.</li>
     *     <li>Already-cold climates become severe without subtracting another
     *         full temperate drop.</li>
     *     <li>Persistently warm climates retain a warm winter floor.</li>
     *     <li>Extremely hot daytime climates retain an additional hot-biome
     *         margin rather than turning every biome into temperate winter.</li>
     * </ul>
     *
     * <p>For Cold Sweat's vanilla defaults this puts Plains near -10..-3 C at
     * full winter intensity, Jungle near 13..19 C, Desert near 6..18 C, and
     * Snowy Plains near -22..-16 C before elevation/weather/local heat.
     */
    private static WinterRange deriveDeepWinterRange(
            double lowTemperature,
            double highTemperature
    )
    {
        double lowC =
                Temperature.convert(
                        lowTemperature,
                        Temperature.Units.MC,
                        Temperature.Units.C,
                        true
                );

        double highC =
                Temperature.convert(
                        highTemperature,
                        Temperature.Units.MC,
                        Temperature.Units.C,
                        true
                );

        double midpointC = (lowC + highC) / 2.0;

        double seasonalDropC;
        if (midpointC < 10.0)
        {
            seasonalDropC =
                    lerp(
                            DEEP_WINTER_COLD_DROP_C,
                            DEEP_WINTER_TEMPERATE_DROP_C,
                            smoothstep(-5.0, 10.0, midpointC)
                    );
        }
        else if (midpointC > 22.0)
        {
            seasonalDropC =
                    lerp(
                            DEEP_WINTER_TEMPERATE_DROP_C,
                            DEEP_WINTER_HOT_DROP_C,
                            smoothstep(22.0, 32.0, midpointC)
                    );
        }
        else
        {
            seasonalDropC = DEEP_WINTER_TEMPERATE_DROP_C;
        }

        double winterMidpointC = midpointC - seasonalDropC;

        /*
         * Warm-biome floor:
         * if a biome's normal nightly low is already persistently warm, deep
         * winter may cool it substantially but should not automatically turn
         * it into Latvia. The transition is smooth so neighboring climate
         * classes do not jump.
         */
        double warmFloorWeight =
                smoothstep(10.0, 14.0, lowC);
        double warmFloorC = lowC - 8.0;

        if (warmFloorC > winterMidpointC)
        {
            winterMidpointC =
                    lerp(
                            winterMidpointC,
                            warmFloorC,
                            warmFloorWeight
                    );
        }

        /*
         * Very hot daytime climates (notably deserts/badlands) preserve some
         * of that identity even in winter.
         */
        winterMidpointC +=
                Math.max(0.0, highC - 35.0) * 0.5;

        double winterSpanC =
                clamp(
                        (highC - lowC) * WINTER_SPAN_SCALE,
                        WINTER_SPAN_MIN_C,
                        WINTER_SPAN_MAX_C
                );

        double winterLowC =
                winterMidpointC - winterSpanC / 2.0;
        double winterHighC =
                winterMidpointC + winterSpanC / 2.0;

        return new WinterRange(
                Temperature.convert(
                        winterLowC,
                        Temperature.Units.C,
                        Temperature.Units.MC,
                        true
                ),
                Temperature.convert(
                        winterHighC,
                        Temperature.Units.C,
                        Temperature.Units.MC,
                        true
                )
        );
    }

    private static double sampleRange(
            double lowTemperature,
            double highTemperature,
            double timeMultiplier
    )
    {
        double clamped =
                clamp(timeMultiplier, -1.0, 1.0);

        double progress = (clamped + 1.0) / 2.0;

        return lowTemperature
                + (highTemperature - lowTemperature) * progress;
    }

    private static double smoothstep(
            double edge0,
            double edge1,
            double value
    )
    {
        if (edge0 == edge1)
        {
            return value >= edge1 ? 1.0 : 0.0;
        }

        double t =
                clamp(
                        (value - edge0) / (edge1 - edge0),
                        0.0,
                        1.0
                );

        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(
            double from,
            double to,
            double progress
    )
    {
        return from + (to - from) * progress;
    }

    private static double clamp(
            double value,
            double min,
            double max
    )
    {
        return Math.max(min, Math.min(max, value));
    }

    public int getSamples()
    {
        return samples;
    }

    private record WinterRange(
            double lowTemperature,
            double highTemperature
    )
    {
    }
}
