package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.WorldTemperatureSettings;
import com.momosoftworks.coldsweat.util.world.WorldTemperatureUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.function.Function;

/**
 * Shade/overcast temperature modifier.
 *
 * M7.12 smooths both the spatial sample and the response over time. A single
 * eye-position skylight lookup could flip the complete upstream -9 F shade
 * offset over one block, which was visible as roughly a 5 C HUD jump.
 *
 * The 3x3 head-level kernel is intentionally tiny: it runs only when this
 * modifier recalculates (currently every 10 ticks), avoids world scans, and
 * makes roof/tree edges behave as partial shade instead of binary switches.
 */
public class ShadeTempModifier extends TempModifier
{
    private static final int SAMPLE_RADIUS = 1;

    /*
     * calculate() currently runs every 10 ticks. 0.5 therefore gives a shade
     * transition that is mostly settled within roughly two seconds without
     * making shelter response feel sluggish.
     */
    private static final double SHADE_RESPONSE_PER_UPDATE = 0.5;

    private double smoothedShade = Double.NaN;

    @Override
    protected Function<Double, Double> calculate(
            LivingEntity entity,
            Temperature.Trait trait
    )
    {
        Level level = entity.level();

        if (level.dimensionType().hasCeiling()
                || !level.dimensionType().hasSkyLight())
        {
            smoothedShade = 0.0;
            return temperature -> temperature;
        }

        BlockPos eyePos =
                BlockPos.containing(entity.getEyePosition());

        BlockPos.MutableBlockPos samplePos =
                new BlockPos.MutableBlockPos();

        double skyBrightnessTotal = 0.0;
        int samples = 0;

        for (int x = -SAMPLE_RADIUS; x <= SAMPLE_RADIUS; x++)
        {
            for (int z = -SAMPLE_RADIUS; z <= SAMPLE_RADIUS; z++)
            {
                samplePos.set(
                        eyePos.getX() + x,
                        eyePos.getY(),
                        eyePos.getZ() + z
                );

                skyBrightnessTotal +=
                        level.getBrightness(
                                LightLayer.SKY,
                                samplePos
                        );
                samples++;
            }
        }

        double averageSkyBrightness =
                samples > 0
                        ? skyBrightnessTotal / samples
                        : level.getBrightness(
                                LightLayer.SKY,
                                eyePos
                        );

        double darkness =
                1.0 - averageSkyBrightness / 15.0;

        darkness *= Math.max(
                0.0,
                WorldTemperatureUtil.getTimeMultiplier(level)
        );

        double overcast =
                level.getRainLevel(1.0F);

        double targetShade =
                clamp(
                        Math.max(darkness, overcast),
                        0.0,
                        1.0
                );

        if (Double.isNaN(smoothedShade))
        {
            smoothedShade = targetShade;
        }
        else
        {
            smoothedShade +=
                    (targetShade - smoothedShade)
                            * SHADE_RESPONSE_PER_UPDATE;

            if (Math.abs(targetShade - smoothedShade) < 1.0e-6)
            {
                smoothedShade = targetShade;
            }
        }

        double shadeAmount =
                WorldTemperatureSettings.getShadeTempOffset()
                        * smoothedShade;

        return temperature -> temperature + shadeAmount;
    }

    private static double clamp(
            double value,
            double min,
            double max
    )
    {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }
}
