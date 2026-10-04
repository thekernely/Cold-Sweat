package com.momosoftworks.coldsweat.config;

/**
 * Fabric-side defaults for critical-temperature damage.
 *
 * Cold is locked for M7:
 * - symptoms/frozen health begin before direct damage;
 * - direct damage starts gently at 33 C;
 * - damage accelerates exponentially as core temperature falls;
 * - by ~31 C survival without rewarming is measured in minutes;
 * - 30.x C rapidly becomes unsustainable.
 *
 * Heat remains provisional until the dedicated hyperthermia balance pass.
 */
public final class TemperatureDamageSettings
{
    public static final double COLD_DAMAGE_START_C = 33.0;

    /**
     * Health points lost per one-second pulse exactly at 33 C.
     * 1 health point = half a heart.
     */
    public static final double COLD_BASE_DAMAGE = 0.020;

    /**
     * Per-degree multiplier below 33 C.
     *
     * 33 C -> 0.020
     * 32 C -> 0.050
     * 31 C -> 0.125
     * 30 C -> 0.3125
     */
    public static final double COLD_DAMAGE_MULTIPLIER_PER_C = 2.5;

    /**
     * Safety cap for extremely deep hypothermia.
     */
    public static final double COLD_MAX_DAMAGE = 1.0;

    /*
     * Provisional heat values - deliberately left separate from the locked
     * cold model so the next pass can change them without touching cold.
     */
    public static final double HOT_DAMAGE_START_C = 41.0;
    public static final double HOT_MIN_DAMAGE = 0.125;
    public static final double HOT_MAX_DAMAGE = 1.0;
    public static final double HOT_DAMAGE_RAMP_C = 2.0;

    /**
     * Once per second at the normal 20 TPS simulation rate.
     */
    public static final int DAMAGE_INTERVAL = 20;

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
