package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.WorldTemperatureSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.function.Function;

/**
 * First Fabric biome-temperature modifier.
 *
 * This restores the standalone vanilla-biome sampling path before the larger
 * Cold Sweat biome/structure override tables are brought across.
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
                total += biome.value().getBaseTemperature();
                collected++;
            }
        }

        double sampledTemperature =
                collected > 0
                        ? total / collected
                        : level.getBiome(center).value().getBaseTemperature();

        double dimensionOffset =
                WorldTemperatureSettings.getDimensionTempOffset(level);

        return temperature ->
                temperature + sampledTemperature + dimensionOffset;
    }

    public int getSamples()
    {
        return samples;
    }
}
