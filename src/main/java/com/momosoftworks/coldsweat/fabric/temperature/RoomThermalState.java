package com.momosoftworks.coldsweat.fabric.temperature;

import com.momosoftworks.coldsweat.api.util.Temperature;

/**
 * Cached thermal state of one connected enclosed air volume.
 *
 * airTemperatureC is a real room-air temperature rather than a direct WORLD
 * delta. The room exchanges heat with the current outdoor climate and
 * accumulates heat from active sources over time.
 */
public record RoomThermalState(
        boolean available,
        boolean enclosed,
        int volume,
        int boundaryFaces,
        int heatSourceBlocks,
        double sourcePower,
        double leakageRatePerSecond,
        double airTemperatureC,
        double outdoorTemperatureC
)
{
    private static final RoomThermalState UNAVAILABLE =
            new RoomThermalState(
                    false, false,
                    0, 0, 0,
                    0.0, 0.0,
                    0.0, 0.0
            );

    public static RoomThermalState unavailable()
    {
        return UNAVAILABLE;
    }

    public double airTemperatureMc()
    {
        if (!available)
        {
            return 0.0;
        }

        return Temperature.convert(
                airTemperatureC,
                Temperature.Units.C,
                Temperature.Units.MC,
                true
        );
    }

    public double retainedDeltaC()
    {
        return available
                ? airTemperatureC - outdoorTemperatureC
                : 0.0;
    }
}
