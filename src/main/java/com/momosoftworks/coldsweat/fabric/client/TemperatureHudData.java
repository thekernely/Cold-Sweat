package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureRuntime;
import net.minecraft.client.player.LocalPlayer;

/**
 * Client-facing snapshot of the synchronized temperature state.
 *
 * Canonical Cold Sweat BODY is a normalized gameplay value. M7.12h promotes
 * the BODY-stress <-> Celsius mapping into TemperatureRuntime so both server
 * physiology and client presentation use exactly the same scale.
 */
public record TemperatureHudData(
        double bodyStress,
        double bodyCelsius,
        double environmentCelsius
)
{
    public static double bodyStressToCelsius(double bodyStress)
    {
        return TemperatureRuntime.bodyStressToCelsius(
                bodyStress
        );
    }

    public static TemperatureHudData capture(LocalPlayer player)
    {
        double bodyStress =
                Temperature.get(player, Temperature.Trait.BODY);

        double worldMc =
                Temperature.get(player, Temperature.Trait.WORLD);

        double environmentCelsius = Temperature.convert(
                worldMc,
                Temperature.Units.MC,
                Temperature.Units.C,
                true
        );

        return new TemperatureHudData(
                bodyStress,
                bodyStressToCelsius(bodyStress),
                environmentCelsius
        );
    }
}
