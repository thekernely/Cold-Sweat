package com.momosoftworks.coldsweat.common.capability.temperature;

/**
 * Loader-independent core body-temperature runtime math.
 *
 * This deliberately does not tick entities yet. M4 will supply environmental
 * world temperature and the modifier chain; this class preserves the core
 * Cold Sweat math that converts those inputs into body-temperature pressure.
 */
public final class TemperatureRuntime
{
    private TemperatureRuntime()
    {
    }

    /**
     * Equivalent to Cold Sweat's CSMath.getSignForRange use in AbstractTempCap:
     * -1 below the habitable minimum, +1 above the maximum, 0 inside the range.
     */
    public static int getWorldTemperatureSign(double worldTemp, double minTemp, double maxTemp)
    {
        if (worldTemp < minTemp)
        {
            return -1;
        }
        if (worldTemp > maxTemp)
        {
            return 1;
        }
        return 0;
    }

    /**
     * Calculates Cold Sweat's raw core-temperature rate before RATE attribute
     * modifiers and entity-climate multipliers are applied.
     *
     * This preserves the upstream behavior:
     * - change scales with distance outside the safe range
     * - a minimum rate of |tempRate / 50| is enforced
     * - cold/heat dampening attenuates positive values and amplifies negative
     *   dampening values
     */
    public static double calculateTemperatureRate(
            double worldTemp,
            double minTemp,
            double maxTemp,
            double tempRate,
            double coldDampening,
            double heatDampening
    )
    {
        int worldTempSign = getWorldTemperatureSign(worldTemp, minTemp, maxTemp);
        if (worldTempSign == 0)
        {
            return 0.0;
        }

        double clampedWorldTemp = Math.max(minTemp, Math.min(maxTemp, worldTemp));
        double difference = Math.abs(worldTemp - clampedWorldTemp);

        double changeBy =
                Math.max(
                        (difference / 7.0) * tempRate,
                        Math.abs(tempRate / 50.0)
                ) * worldTempSign;

        if (changeBy < 0)
        {
            return applyDampening(changeBy, coldDampening);
        }
        if (changeBy > 0)
        {
            return applyDampening(changeBy, heatDampening);
        }
        return 0.0;
    }

    /**
     * Cold Sweat's dampening blend for one side of the temperature spectrum.
     *
     * Negative dampening makes temperature movement stronger.
     * 0..1 dampening linearly blends the movement toward zero.
     */
    public static double applyDampening(double change, double dampening)
    {
        if (dampening < 0)
        {
            return change * (1.0 + Math.abs(dampening));
        }

        // Equivalent to CSMath.blend(change, 0, dampening, 0, 1)
        double factor = Math.max(0.0, Math.min(1.0, dampening));
        return change + (0.0 - change) * factor;
    }
}
