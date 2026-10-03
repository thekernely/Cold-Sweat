package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.WorldTemperatureSettings;
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
 * A single biome boundary can therefore only replace a small fraction of the
 * climate sample instead of changing the player's apparent surroundings by
 * several degrees at once.
 */
public class BiomeTempModifier extends TempModifier
{
    private static final int SAMPLE_SPACING_BLOCKS = 16;

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
            Holder<Biome> biome,
            double timeMultiplier
    )
    {
        Optional<Identifier> biomeId =
                biome.unwrapKey()
                        .map(ResourceKey::identifier);

        if (biomeId.isPresent())
        {
            Optional<WorldTemperatureSettings.BiomeTemperatureRange> range =
                    WorldTemperatureSettings.getBiomeTemperatureRange(
                            biomeId.get()
                    );

            if (range.isPresent())
            {
                return range.get().sample(timeMultiplier);
            }
        }

        return biome.value().getBaseTemperature();
    }

    public int getSamples()
    {
        return samples;
    }
}
