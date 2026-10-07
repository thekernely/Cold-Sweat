package com.momosoftworks.coldsweat.fabric.temperature;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;

/**
 * M9.4a wind/exposure contribution to the player-facing apparent environment.
 *
 * Wind belongs to WORLD/apparent temperature, not directly to CORE. Geometry
 * comes from the existing cached EnvironmentSnapshotScanner pass; this model
 * performs no additional world scan.
 *
 * Wetness remains separately owned by WaterTempModifier/ThermoregulationRuntime.
 * Rain and thunder only strengthen the airflow term here, so a wet player is
 * not represented by a second arbitrary "wet = colder air" penalty.
 *
 * Hot-weather evaporative cooling, directional wind, and richer Ecliptic
 * weather coupling are intentionally deferred. This first slice establishes
 * the ownership boundary and cold-side survival behavior.
 */
public final class WindExposureModel
{
    /*
     * Wind chill fades smoothly to zero by 10 C. At -20 C and below the
     * cold-side severity reaches full strength. These are gameplay envelopes,
     * not an attempt to infer real-world wind speed from Minecraft weather.
     */
    private static final double WIND_CHILL_START_C = 10.0;
    private static final double FULL_WIND_CHILL_C = -20.0;

    /*
     * Clear exposed winter air may lower apparent temperature by at most 2 C.
     * Rain and thunderstorms can strengthen that to 3.3 C / 4.5 C. This keeps
     * wind meaningful without stacking another giant flat winter penalty on
     * top of seasonal climate and wetness physiology.
     */
    private static final double MAX_CLEAR_EXPOSED_CHILL_C = 2.0;
    private static final double FULL_RAIN_MULTIPLIER = 1.65;
    private static final double THUNDER_MULTIPLIER = 2.25;

    /*
     * Local cover should substantially reduce wind without pretending an
     * open-sided shelter is the same as a retained-air room. The scanner
     * recognizes direct overhead cover and meaningful nearby roof coverage;
     * broad skyExposure still distinguishes small cover from broad enclosure.
     */
    private static final double LOCAL_SHELTER_MULTIPLIER = 0.35;

    private WindExposureModel()
    {
    }

    /**
     * @param currentApparentMc apparent WORLD temperature before wind
     * @return a relative MC-temperature delta to add to WORLD
     */
    public static double apparentTemperatureDelta(
            LivingEntity entity,
            EnvironmentSnapshot.SpatialState spatial,
            RoomThermalState room,
            double currentApparentMc
    )
    {
        if (entity == null
                || spatial == null
                || !spatial.available())
        {
            return 0.0;
        }

        /*
         * Retained room air already owns enclosure/ventilation thermals.
         * Applying outdoor wind chill again inside that reservoir would
         * double-count open-door heat loss. Underground spaces likewise have
         * no meaningful outdoor wind exposure.
         */
        if ((room != null && room.available())
                || spatial.underground())
        {
            return 0.0;
        }

        double exposure =
                clamp01(spatial.skyExposure());

        if (spatial.sheltered())
        {
            exposure *= LOCAL_SHELTER_MULTIPLIER;
        }

        if (exposure <= 1.0e-6)
        {
            return 0.0;
        }

        double apparentC =
                Temperature.convert(
                        currentApparentMc,
                        Temperature.Units.MC,
                        Temperature.Units.C,
                        true
                );

        if (apparentC >= WIND_CHILL_START_C)
        {
            return 0.0;
        }

        double coldSeverity =
                clamp01(
                        (WIND_CHILL_START_C - apparentC)
                                / (WIND_CHILL_START_C - FULL_WIND_CHILL_C)
                );

        double rain =
                clamp01(entity.level().getRainLevel(1.0F));

        double weatherMultiplier =
                1.0
                        + rain * (FULL_RAIN_MULTIPLIER - 1.0);

        if (entity.level().isThundering())
        {
            weatherMultiplier =
                    Math.max(
                            weatherMultiplier,
                            THUNDER_MULTIPLIER
                    );
        }

        double chillC =
                MAX_CLEAR_EXPOSED_CHILL_C
                        * weatherMultiplier
                        * coldSeverity
                        * exposure;

        if (chillC <= 1.0e-9)
        {
            return 0.0;
        }

        return Temperature.convert(
                -chillC,
                Temperature.Units.C,
                Temperature.Units.MC,
                false
        );
    }

    private static double clamp01(double value)
    {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }
}
