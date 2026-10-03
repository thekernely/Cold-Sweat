package com.momosoftworks.coldsweat.fabric.temperature;

import com.momosoftworks.coldsweat.api.util.Temperature;

/**
 * Converts non-air environmental loads into the player-facing apparent
 * environment used by WORLD.
 *
 * M7.12f-d starts with radiant heat only. The radiation coefficient and globe
 * weighting are based on the same black-globe relationship used by
 * Homeostatic, but we deliberately extract only the radiation contribution.
 * Ambient climate is already calculated by Cold Sweat and must not be counted
 * a second time here.
 */
public final class ApparentTemperatureModel
{
    /*
     * Homeostatic black-globe relation:
     * blackGlobeTempC += 0.01498 * radiation
     *
     * Its exposed environment gives black-globe temperature 20% weight, while
     * sheltered/underground environments give it 30% weight. Applying only
     * that radiation term yields the coefficients below.
     */
    private static final double BLACK_GLOBE_RADIATION_C =
            0.01498;

    private static final double EXPOSED_GLOBE_WEIGHT =
            0.20;

    private static final double SHELTERED_GLOBE_WEIGHT =
            0.30;

    private ApparentTemperatureModel()
    {
    }

    /**
     * Apparent temperature delta caused by nearby radiant heat.
     *
     * @return Cold Sweat MC-temperature delta, not an absolute temperature.
     */
    public static double radiantTemperatureDelta(
            EnvironmentSnapshot.SpatialState spatial
    )
    {
        if (spatial == null
                || !spatial.available()
                || spatial.radiantLoad() <= 0.0)
        {
            return 0.0;
        }

        double globeWeight =
                spatial.sheltered()
                        || spatial.underground()
                        ? SHELTERED_GLOBE_WEIGHT
                        : EXPOSED_GLOBE_WEIGHT;

        double apparentDeltaC =
                spatial.radiantLoad()
                        * BLACK_GLOBE_RADIATION_C
                        * globeWeight;

        return Temperature.convert(
                apparentDeltaC,
                Temperature.Units.C,
                Temperature.Units.MC,
                false
        );
    }
}
