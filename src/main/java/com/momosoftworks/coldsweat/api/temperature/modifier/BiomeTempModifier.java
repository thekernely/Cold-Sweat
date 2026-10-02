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
 * Uses Cold Sweat's upstream vanilla biome ranges when configured, blended
 * between coldest/hottest values using the world time multiplier. Biomes that
 * are absent or explicitly disabled in the upstream table retain a vanilla
 * base-temperature fallback until the full data/config pipeline is restored.
 */
public class BiomeTempModifier extends TempModifier
{
    private final int samples;

    public BiomeTempModifier()
    {
        this(16);
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

        for (int x = 0; x < side && collected < samples; x++)
        {
            for (int z = 0; z < side && collected < samples; z++)
            {
                int xOffset = (x - half) * 10;
                int zOffset = (z - half) * 10;
                BlockPos samplePos = new BlockPos(
                        center.getX() + xOffset,
                        center.getY(),
                        center.getZ() + zOffset
                );

                Holder<Biome> biome = level.getBiome(samplePos);

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
                temperature + sampledTemperature + dimensionOffset;
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
