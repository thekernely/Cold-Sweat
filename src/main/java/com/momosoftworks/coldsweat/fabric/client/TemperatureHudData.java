package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.client.player.LocalPlayer;

/**
 * Client-facing snapshot of the synchronized temperature state.
 *
 * Canonical Cold Sweat BODY is a normalized gameplay value. It is deliberately
 * kept separate from the player-facing body-temperature presentation so the
 * latter can evolve without changing server simulation semantics.
 */
public record TemperatureHudData(
        double bodyStress,
        double bodyCelsius,
        double environmentCelsius
)
{
    private static final double NORMAL_BODY_C = 37.0;

    /**
     * First-pass presentation mapping for the user-facing body-temperature HUD.
     *
     * Anchors are intentionally presentation-only:
     *  BODY   0 -> 37 C
     *  BODY -50 -> 35 C
     *  BODY -100 -> 33 C
     *  BODY +50 -> 41 C
     *  BODY +100 -> 43 C
     *
     * These values make the normalized Cold Sweat stress scale readable in
     * familiar physiological terms while preserving BODY itself unchanged.
     * They can later become config-driven without touching the runtime model.
     */
    public static double bodyStressToCelsius(double bodyStress)
    {
        if (bodyStress <= 0.0)
        {
            return NORMAL_BODY_C + bodyStress * 0.04;
        }

        if (bodyStress <= 50.0)
        {
            return NORMAL_BODY_C + bodyStress * 0.08;
        }

        return 41.0 + (bodyStress - 50.0) * 0.04;
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
