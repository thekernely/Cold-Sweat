package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.WorldTemperatureSettings;
import com.momosoftworks.coldsweat.data.tag.ModBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;
import java.util.function.Function;

/**
 * Underground-biome temperature blending.
 *
 * Mirrors upstream CaveBiomeTempModifier's 3D sampling model:
 * - sample a cube around the entity at six-block intervals
 * - only consider samples below the local surface
 * - only consider biomes in c:is_underground
 * - blend toward the average cave-biome temperature according to the number
 *   of underground samples found
 */
public class CaveBiomeTempModifier extends TempModifier
{
    private final int sampleRoot;

    public CaveBiomeTempModifier()
    {
        this(6);
    }

    public CaveBiomeTempModifier(int samples)
    {
        this.sampleRoot = Math.max(1, samples);
    }

    @Override
    protected Function<Double, Double> calculate(
            LivingEntity entity,
            Temperature.Trait trait
    )
    {
        Level level = entity.level();
        BlockPos center = entity.blockPosition();

        int radius = (sampleRoot * 6) / 2;
        int halfInterval = 3;

        double biomeTempTotal = 0.0;
        int caveBiomeCount = 0;

        for (int x = -radius + halfInterval;
             x < radius + halfInterval;
             x += 6)
        {
            for (int y = -radius + halfInterval;
                 y < radius + halfInterval;
                 y += 6)
            {
                for (int z = -radius + halfInterval;
                     z < radius + halfInterval;
                     z += 6)
                {
                    BlockPos samplePos = center.offset(x, y, z);

                    if (!level.isInWorldBounds(samplePos))
                    {
                        continue;
                    }

                    int surfaceY = level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING,
                            samplePos
                    );

                    if (surfaceY <= entity.getY())
                    {
                        continue;
                    }

                    Holder<Biome> biome = level.getBiome(samplePos);
                    if (!biome.is(ModBiomeTags.IS_UNDERGROUND))
                    {
                        continue;
                    }

                    Optional<Identifier> biomeId =
                            biome.unwrapKey().map(ResourceKey::identifier);

                    /*
                     * Upstream's default table explicitly disables
                     * minecraft:dripstone_caves.
                     */
                    if (biomeId.isPresent()
                            && biomeId.get().equals(
                                    Identifier.withDefaultNamespace(
                                            "dripstone_caves"
                                    )
                            ))
                    {
                        continue;
                    }

                    double biomeTemp =
                            biomeId.flatMap(
                                    WorldTemperatureSettings
                                            ::getBiomeTemperatureRange
                            )
                            .map(range ->
                                    (range.lowTemperature()
                                            + range.highTemperature()) / 2.0
                            )
                            .orElseGet(
                                    () -> (double) biome.value()
                                            .getBaseTemperature()
                            );

                    biomeTempTotal += biomeTemp;
                    caveBiomeCount++;
                }
            }
        }

        if (caveBiomeCount == 0)
        {
            return temperature -> temperature;
        }

        double biomeTempAverage =
                biomeTempTotal / caveBiomeCount;

        double sampleCapacity =
                Math.pow(sampleRoot, 3);

        double blend =
                Math.max(
                        0.0,
                        Math.min(
                                1.0,
                                caveBiomeCount / sampleCapacity
                        )
                );

        return temperature ->
                temperature
                        + (biomeTempAverage - temperature) * blend;
    }

    public int getSamples()
    {
        return sampleRoot;
    }
}
