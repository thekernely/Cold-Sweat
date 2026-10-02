package com.momosoftworks.coldsweat.config;

/**
 * Fabric-side defaults for critical-temperature damage.
 *
 * These mirror upstream main settings until the full file-backed config bridge
 * is restored.
 */
public final class TemperatureDamageSettings
{
    public static final double TEMPERATURE_DAMAGE = 2.0;
    public static final int HURT_INTERVAL = 40;

    public static final boolean FIRE_RESISTANCE_ENABLED = true;
    public static final boolean ICE_RESISTANCE_ENABLED = true;

    private TemperatureDamageSettings()
    {
    }
}
