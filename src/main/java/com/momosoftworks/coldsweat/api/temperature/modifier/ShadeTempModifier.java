package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.util.world.WorldTemperatureUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.function.Function;

/**
 * Shade/overcast temperature modifier.
 *
 * Upstream's default world.toml value is -9 F. As a relative temperature
 * offset, Cold Sweat converts that to -0.2 Minecraft temperature units.
 * The full config bridge will replace this constant later.
 */
public class ShadeTempModifier extends TempModifier
{
    public static final double DEFAULT_SHADE_TEMP_OFFSET =
            Temperature.convert(-9.0, Temperature.Units.F, Temperature.Units.MC, false);

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
            return temperature -> temperature;
        }

        BlockPos eyePos = BlockPos.containing(entity.getEyePosition());

        double darkness =
                1.0 - (level.getBrightness(LightLayer.SKY, eyePos) / 15.0);

        darkness *= Math.max(
                0.0,
                WorldTemperatureUtil.getTimeMultiplier(level)
        );

        double overcast = level.getRainLevel(1.0F);
        double shade = Math.max(darkness, overcast);

        double shadeAmount = DEFAULT_SHADE_TEMP_OFFSET
                * Math.max(0.0, Math.min(1.0, shade));

        return temperature -> temperature + shadeAmount;
    }
}
