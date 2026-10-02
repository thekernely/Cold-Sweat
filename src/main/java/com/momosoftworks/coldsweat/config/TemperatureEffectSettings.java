package com.momosoftworks.coldsweat.config;

/**
 * Fabric-side defaults for temperature-driven gameplay effects.
 *
 * These mirror Cold Sweat's default difficulty settings until the full
 * file-backed config bridge is restored.
 */
public final class TemperatureEffectSettings
{
    public static final double COLD_EFFECT_START = -50.0;
    public static final double COLD_EFFECT_MAX = -100.0;

    public static final double COLD_MOVEMENT_SLOWDOWN = 0.5;
    public static final double COLD_MINING_IMPAIRMENT = 0.5;
    public static final double HEARTS_FREEZING_PERCENTAGE = 0.5;
    public static final double COLD_KNOCKBACK_REDUCTION = 0.5;

    private TemperatureEffectSettings()
    {
    }
}
