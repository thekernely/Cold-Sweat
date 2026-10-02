package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.WaterExposureSettings;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Cold Sweat's WORLD-trait wetness modifier.
 *
 * M4.12 restores the upstream soak/rain/dry state machine and defaults. The
 * full biome-specific water-temperature lookup is intentionally deferred until
 * the biome config/data bridge exists; until then water uses world.toml's
 * default -10 F relative contribution.
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

        double finalTemperature = newTemperature;
        return temp -> temp + finalTemperature;
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
