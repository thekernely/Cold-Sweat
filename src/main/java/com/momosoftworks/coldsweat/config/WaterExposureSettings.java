package com.momosoftworks.coldsweat.config;

import com.momosoftworks.coldsweat.api.util.Temperature;

/**
 * Fabric-side defaults for Cold Sweat's wetness / water-temperature system.
 *
 * These values mirror upstream world.toml. File-backed configuration is
 * restored later; keeping them behind this boundary avoids baking constants
 * into WaterTempModifier itself.
 */
public final class WaterExposureSettings
{
    public static final double DRYOFF_SPEED = 0.0015;
    public static final double WATER_SOAK_SPEED = 0.1;
    public static final double RAIN_SOAK_SPEED = 0.0125;
    public static final double MAX_RAIN_SOAK = 0.2;

    /**
     * Upstream world.toml default: -10 F.
     *
     * WaterTempModifier stores a relative WORLD-temperature contribution, so
     * this is converted as an offset rather than an absolute temperature.
     */
    public static final double DEFAULT_WATER_TEMP_DELTA =
            Temperature.convert(
                    -10.0,
                    Temperature.Units.F,
                    Temperature.Units.MC,
                    false
            );

    /*
     * Current M3 defaults. These become config-backed with the difficulty
     * bridge; they are the same fallback thresholds used by Cold Sweat's core.
     */
    public static final double FREEZING_POINT = 0.5;
    public static final double BURNING_POINT = 1.7;

    private WaterExposureSettings()
    {
    }
}
