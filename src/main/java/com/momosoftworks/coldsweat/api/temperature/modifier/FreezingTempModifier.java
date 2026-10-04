package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Compatibility lifecycle modifier for vanilla freezing exposure.
 *
 * M7.12q deliberately stops applying vanilla frozen ticks directly to BASE.
 *
 * The old behavior subtracted up to 20 BASE points while the entity was
 * freezing. Because BODY = CORE + BASE, entering powdered snow made the HUD and
 * physiology appear to cool instantly, and leaving it made BODY rebound just
 * as quickly as vanilla frozen ticks decayed even though CORE had barely moved.
 *
 * Vanilla freezing is now consumed by SurfaceTemperatureRuntime, where it cools
 * the fast surface layer first. The surface/core gradient can then cool CORE
 * with inertia, so leaving powdered snow does not magically restore body
 * temperature.
 *
 * This modifier remains as a no-op compatibility/lifecycle entry for now so the
 * surrounding modifier ownership code does not need a risky rewrite in the
 * same regression-fix slice.
 */
public class FreezingTempModifier extends TempModifier
{
    @Override
    protected Function<Double, Double> calculate(
            LivingEntity entity,
            Temperature.Trait trait
    )
    {
        if (entity.getTicksFrozen() <= 0)
        {
            expires(0);
        }

        return temperature -> temperature;
    }
}
