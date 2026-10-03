package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.WaterExposureSettings;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Cold Sweat's water/rain exposure state.
 *
 * M7.12g separates wetness from literal WORLD temperature. Rain now builds and
 * dries the same persistent wetness state without pretending that a wet player
 * makes the surrounding air several extra degrees colder. Direct immersion
 * still applies the configured water-temperature delta to WORLD for the
 * transitional M7 environment model.
 *
 * The tracked wetness fraction is intentionally exposed for the upcoming
 * high-inertia body regulator, where rain-soaked clothing/skin can increase
 * heat loss without double-counting it as colder ambient air.
 */
public class WaterTempModifier extends TempModifier
{
    private double temperature;

    public WaterTempModifier()
    {
        this(0.0);
    }

    public WaterTempModifier(double temperature)
    {
        this.temperature = temperature;
    }

    public double getTemperature()
    {
        return temperature;
    }

    public void setTemperature(double temperature)
    {
        if (Double.compare(this.temperature, temperature) != 0)
        {
            markDirty();
        }
        this.temperature = temperature;
    }

    public double getTargetTemperature(LivingEntity entity)
    {
        return WaterExposureSettings.DEFAULT_WATER_TEMP_DELTA;
    }

    /**
     * Normalized 0..1 wetness signal for physiology.
     *
     * Rain saturation is normalized against MAX_RAIN_SOAK. While immersed,
     * the configured water-temperature target is used so a fully-soaked player
     * still reports approximately 1.0 even if those configured magnitudes
     * differ slightly.
     */
    public double getWetnessFraction(LivingEntity entity)
    {
        double scale = entity.isInWater()
                ? Math.abs(getTargetTemperature(entity))
                : WaterExposureSettings.MAX_RAIN_SOAK;

        if (scale <= 1.0e-9)
        {
            return 0.0;
        }

        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        Math.abs(getTemperature()) / scale
                )
        );
    }

    @Override
    protected Function<Double, Double> calculate(
            LivingEntity entity,
            Temperature.Trait trait
    )
    {
        double worldTemp =
                Temperature.get(entity, Temperature.Trait.WORLD);

        double current = getTemperature();
        double target = getTargetTemperature(entity);

        boolean inWater = entity.isInWater();
        boolean raining =
                entity.level().isRainingAt(entity.blockPosition());

        double addAmount;
        if (inWater)
        {
            if (current < target)
            {
                addAmount = Math.min(
                        WaterExposureSettings.WATER_SOAK_SPEED,
                        target - current
                );
            }
            else
            {
                addAmount = Math.max(
                        -WaterExposureSettings.WATER_SOAK_SPEED,
                        target - current
                );
            }
        }
        else if (raining)
        {
            addAmount = Math.max(
                    -WaterExposureSettings.RAIN_SOAK_SPEED,
                    -WaterExposureSettings.MAX_RAIN_SOAK - current
            );
        }
        else
        {
            addAmount = 0.0;
        }

        double dryAmount = inWater
                ? 0.0
                : blendExp(
                        WaterExposureSettings.DRYOFF_SPEED / 1.5,
                        WaterExposureSettings.DRYOFF_SPEED * 5.0,
                        worldTemp,
                        WaterExposureSettings.FREEZING_POINT,
                        WaterExposureSettings.BURNING_POINT,
                        20.0
                );

        double tickScale = getTickRate() / 5.0;

        double newTemperature = shrink(
                current + addAmount * tickScale,
                dryAmount * tickScale
        );

        if (Math.abs(newTemperature) < 1.0e-9)
        {
            newTemperature = 0.0;
            expires(0);
        }

        setTemperature(newTemperature);

        /*
         * M7.12g semantic split:
         *
         * - immersion still changes the effective environment for now because
         *   the player is physically surrounded by water whose temperature
         *   matters;
         * - rain only updates wetness state. Its physiological cooling belongs
         *   in the body/skin regulator instead of being counted as colder air.
         *
         * This removes the rain double-count discovered during M7.12f testing
         * while preserving the existing soak/dry lifecycle and a clean M8 hook.
         */
        double directEnvironmentDelta =
                inWater
                        ? newTemperature
                        : 0.0;

        return temp -> temp + directEnvironmentDelta;
    }

    @Override
    public void tick(LivingEntity entity)
    {
        /*
         * Upstream also renders wetness particles client-side. Client modifier
         * rendering waits for M7 so the server remains the only authority here.
         */
        if (!entity.level().isClientSide() && entity.isOnFire())
        {
            setTemperature(
                    shrink(getTemperature(), 0.1)
            );
            entity.clearFire();
        }
    }

    private static double shrink(double value, double amount)
    {
        if (value == 0.0)
        {
            return 0.0;
        }

        return Math.max(
                0.0,
                Math.abs(value) - amount
        ) * Math.signum(value);
    }

    private static double blendExp(
            double from,
            double to,
            double factor,
            double rangeMin,
            double rangeMax,
            double intensity
    )
    {
        factor = Math.max(
                rangeMin,
                Math.min(rangeMax, factor)
        );

        double normalized =
                (factor - rangeMin) / (rangeMax - rangeMin);

        double expFactor =
                (Math.pow(intensity, normalized) - 1.0)
                        / (intensity - 1.0);

        return from + (to - from) * expFactor;
    }
}
