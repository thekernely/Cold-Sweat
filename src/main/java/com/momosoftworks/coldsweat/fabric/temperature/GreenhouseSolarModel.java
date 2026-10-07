package com.momosoftworks.coldsweat.fabric.temperature;

import com.momosoftworks.coldsweat.util.world.WorldTemperatureUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Converts measured greenhouse roof geometry into solar heat source power.
 *
 * The room scanner owns geometry. This model owns the environmental forcing:
 * solar gain follows the existing Cold Sweat diurnal thermal phase, is reduced
 * by locally-resolved precipitation/overcast, and becomes ordinary source
 * power consumed by the same room-air reservoir as furnaces and other heaters.
 */
public final class GreenhouseSolarModel
{
    /*
     * Calibrated so a compact 5x5x3 test greenhouse with a fully glazed roof
     * settles several degrees above ambient under full sun rather than gaining
     * an arbitrary flat temperature bonus. Larger/taller rooms dilute the same
     * aperture energy naturally through room volume.
     */
    private static final double FULL_SUN_POWER_PER_GLAZING_FACE = 0.10;

    /*
     * Rain represents heavy overcast here. Some diffuse solar energy still
     * reaches glazing, but only a quarter of clear-sky input at full rain.
     */
    private static final double FULL_RAIN_TRANSMISSION = 0.25;

    private GreenhouseSolarModel()
    {
    }

    public static double calculateHeatPower(
            ServerLevel level,
            EnvironmentSnapshotScanner.RoomSample sample
    )
    {
        if (sample == null
                || !sample.available()
                || !sample.enclosed()
                || sample.solarGlazingFaces() <= 0
                || !level.dimensionType().hasSkyLight()
                || level.dimensionType().hasCeiling())
        {
            return 0.0;
        }

        double solarPhase =
                clamp(
                        WorldTemperatureUtil.getTimeMultiplier(level),
                        0.0,
                        1.0
                );

        if (solarPhase <= 0.0)
        {
            return 0.0;
        }

        BlockPos weatherProbe =
                roomWeatherProbe(sample);

        double precipitationStrength =
                WeatherExposureModel.sample(
                        level,
                        weatherProbe
                ).precipitationStrength();

        double weatherTransmission =
                1.0
                        - precipitationStrength
                        * (1.0 - FULL_RAIN_TRANSMISSION);

        return sample.solarGlazingFaces()
                * FULL_SUN_POWER_PER_GLAZING_FACE
                * solarPhase
                * weatherTransmission;
    }

    /**
     * Sample the weather column immediately above the retained room.
     *
     * The room scan already owns geometry and records the room bounds. Using
     * their horizontal center keeps this weather lookup O(1) and avoids a
     * second roof search. solarGlazingFaces > 0 already guarantees that at
     * least part of the roof is genuinely sky-exposed.
     */
    private static BlockPos roomWeatherProbe(
            EnvironmentSnapshotScanner.RoomSample sample
    )
    {
        EnvironmentSnapshotScanner.RoomKey key =
                sample.key();

        if (key == null)
        {
            return BlockPos.ZERO;
        }

        int x =
                key.minX()
                        + (key.maxX() - key.minX()) / 2;
        int z =
                key.minZ()
                        + (key.maxZ() - key.minZ()) / 2;

        return new BlockPos(
                x,
                key.maxY() + 2,
                z
        );
    }

    private static double clamp(
            double value,
            double min,
            double max
    )
    {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }
}
