package com.momosoftworks.coldsweat.config;

/**
 * Fabric-side defaults for critical-temperature damage.
 *
 * M7 deliberately changes the damage feel from upstream's larger, variable
 * interval hits to small fixed environmental pulses:
 * - half a heart (1 health point)
 * - every 10 ticks / 0.5 seconds
 *
 * The old inline M4 damage path remains present for parity reference but is
 * disabled with HURT_INTERVAL = 0. TemperatureDamageRuntime owns live damage.
 */
public final class TemperatureDamageSettings
{
    public static final double TEMPERATURE_DAMAGE = 1.0;
    public static final int DAMAGE_INTERVAL = 10;

    /**
     * Legacy TemperatureModifierRuntime damage gate.
     * Zero disables that path so damage is never applied twice.
     */
    public static final int HURT_INTERVAL = 0;

    public static final boolean FIRE_RESISTANCE_ENABLED = true;
    public static final boolean ICE_RESISTANCE_ENABLED = true;

    private TemperatureDamageSettings()
    {
    }
}
