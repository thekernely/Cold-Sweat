package com.momosoftworks.coldsweat.api.temperature.block_temp;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Small Fabric-side equivalent of a static config-backed BlockTemp.
 *
 * It preserves Cold Sweat's unit semantics:
 * - source temperature and max effect are relative values
 * - world-temperature limits are absolute values
 */
public final class StaticBlockTemp extends SimpleBlockTemp
{
    private final double temperature;
    private final TagKey<Block> tag;
    private final Predicate<BlockState> statePredicate;
    private final boolean invertSoulFire;

    private StaticBlockTemp(
            double temperature,
            double range,
            double maxEffect,
            double minTemp,
            double maxTemp,
            boolean logarithmic,
            TagKey<Block> tag,
            Predicate<BlockState> statePredicate,
            boolean invertSoulFire,
            Block... blocks
    )
    {
        super(
                -Math.abs(maxEffect),
                Math.abs(maxEffect),
                minTemp,
                maxTemp,
                range,
                true,
                logarithmic,
                blocks
        );

        this.temperature = temperature;
        this.tag = tag;
        this.statePredicate =
                statePredicate != null
                        ? statePredicate
                        : state -> true;
        this.invertSoulFire = invertSoulFire;
    }

    public static StaticBlockTemp forBlockFahrenheit(
            Block block,
            double temperatureF,
            double range,
            double maxEffectF,
            Double temperatureLimitF,
            boolean logarithmic
    )
    {
        double temperature = Temperature.convert(
                temperatureF,
                Temperature.Units.F,
                Temperature.Units.MC,
                false
        );

        double maxEffect = Temperature.convert(
                maxEffectF,
                Temperature.Units.F,
                Temperature.Units.MC,
                false
        );

        double minTemp = Double.NEGATIVE_INFINITY;
        double maxTemp = Double.POSITIVE_INFINITY;

        if (temperatureLimitF != null)
        {
            double limit = Temperature.convert(
                    temperatureLimitF,
                    Temperature.Units.F,
                    Temperature.Units.MC,
                    true
            );

            if (temperatureF > 0)
            {
                maxTemp = limit;
            }
            else if (temperatureF < 0)
            {
                minTemp = limit;
            }
        }

        return new StaticBlockTemp(
                temperature,
                range,
                maxEffect,
                minTemp,
                maxTemp,
                logarithmic,
                null,
                state -> true,
                false,
                block
        );
    }

    public static StaticBlockTemp forTagFahrenheit(
            TagKey<Block> tag,
            double temperatureF,
            double range,
            double maxEffectF,
            Double temperatureLimitF,
            Predicate<BlockState> statePredicate,
            boolean invertSoulFire
    )
    {
        double temperature = Temperature.convert(
                temperatureF,
                Temperature.Units.F,
                Temperature.Units.MC,
                false
        );

        double maxEffect = Temperature.convert(
                maxEffectF,
                Temperature.Units.F,
                Temperature.Units.MC,
                false
        );

        double minTemp = Double.NEGATIVE_INFINITY;
        double maxTemp = Double.POSITIVE_INFINITY;

        if (temperatureLimitF != null)
        {
            double limit = Temperature.convert(
                    temperatureLimitF,
                    Temperature.Units.F,
                    Temperature.Units.MC,
                    true
            );

            if (temperatureF > 0)
            {
                maxTemp = limit;
            }
            else if (temperatureF < 0)
            {
                minTemp = limit;
            }
        }

        return new StaticBlockTemp(
                temperature,
                range,
                maxEffect,
                minTemp,
                maxTemp,
                false,
                tag,
                statePredicate,
                invertSoulFire
        );
    }

    @Override
    public boolean matches(BlockState state)
    {
        return tag != null
                ? state.is(tag)
                : super.matches(state);
    }

    @Override
    public boolean isValid(
            Level level,
            BlockPos pos,
            BlockState state
    )
    {
        return matches(state) && statePredicate.test(state);
    }

    @Override
    public double getTemperature(
            Level level,
            LivingEntity entity,
            BlockState state,
            BlockPos pos,
            double distance
    )
    {
        if (invertSoulFire
                && (state.is(Blocks.SOUL_FIRE)
                    || state.is(Blocks.SOUL_CAMPFIRE)))
        {
            return -temperature;
        }

        return temperature;
    }
}
