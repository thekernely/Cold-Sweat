package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.InsulationSettings;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Reduces outward body-temperature RATE using the cold/heat insulation values
 * calculated from the player's equipped armor.
 *
 * Upstream stores these two values in TempModifier NBT. The Fabric 26.2 port's
 * loader-independent TempModifier intentionally has no NBT dependency, so the
 * same runtime state is held directly on the modifier instance instead.
 */
public final class ArmorInsulationTempModifier extends TempModifier
{
    private final double cold;
    private final double heat;

    public ArmorInsulationTempModifier()
    {
        this(0.0, 0.0);
    }

    public ArmorInsulationTempModifier(double cold, double heat)
    {
        this.cold = cold;
        this.heat = heat;
    }

    public double cold()
    {
        return cold;
    }

    public double heat()
    {
        return heat;
    }

    @Override
    protected Function<Double, Double> calculate(
            LivingEntity entity,
            Temperature.Trait trait
    )
    {
        double insulationStrength = InsulationSettings.INSULATION_STRENGTH;

        return temperature ->
        {
            double insulation =
                    (temperature > 0.0 ? heat : cold)
                            * insulationStrength;

            if (insulation >= 0.0)
            {
                return temperature * Math.pow(0.1, insulation / 40.0);
            }

            return temperature * (-insulation / 20.0 + 1.0);
        };
    }
}
