package com.momosoftworks.coldsweat.config;

/**
 * Fabric-side defaults for critical-temperature damage.
 *
 * Locked M7 systemic physiology:
 *
 * Cold:
 * - symptoms/frozen health begin before direct damage;
 * - direct damage starts gently at 33 C;
 * - damage accelerates exponentially as core temperature falls;
 * - by ~31 C survival without rewarming is measured in minutes.
 *
 * Heat:
 * - symptoms begin before direct damage;
 * - direct hyperthermia damage starts gently at 41 C;
 * - damage accelerates exponentially as core temperature rises;
 * - by ~43 C survival without cooling is measured in minutes.
 *
 * M7.12p adds a separate fast surface-temperature layer. Surface injury is
 * driven by skin/surface Celsius, not directly by WORLD or raw radiation.
 */
public final class TemperatureDamageSettings
{
    public static final double COLD_DAMAGE_START_C = 33.0;
    public static final double COLD_BASE_DAMAGE = 0.020;
    public static final double COLD_DAMAGE_MULTIPLIER_PER_C = 2.5;
    public static final double COLD_MAX_DAMAGE = 1.0;

    public static final double HOT_DAMAGE_START_C = 41.0;
    public static final double HOT_BASE_DAMAGE = 0.020;
    public static final double HOT_DAMAGE_MULTIPLIER_PER_C = 2.5;
    public static final double HOT_MAX_DAMAGE = 1.0;

    /*
     * Surface/scalding path.
     *
     * Around 55 C surface temperature, direct thermal injury begins.
     * By 65 C it is severe. This is deliberately separate from systemic
     * hyperthermia: a player can be burned before their high-inertia core has
     * had time to rise to 41 C.
     */
    public static final double SCALDING_DAMAGE_START_C = 55.0;
    public static final double SCALDING_DAMAGE_FULL_C = 65.0;
    public static final double SCALDING_MAX_DAMAGE = 1.5;

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
