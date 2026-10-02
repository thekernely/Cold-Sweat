package com.momosoftworks.coldsweat.common.capability.temperature;

/**
 * Loader-independent core body-temperature runtime math.
 */
public final class TemperatureRuntime
{
    public static final double DEFAULT_FREEZING_POINT = 0.5;
    public static final double DEFAULT_BURNING_POINT = 1.7;
    public static final double DEFAULT_TEMP_RATE = 1.0;

    private TemperatureRuntime()
    {
    }

    /**
     * Equivalent to Cold Sweat's CSMath.getSignForRange use in AbstractTempCap:
     * -1 below the habitable minimum, +1 above the maximum, 0 inside the range.
     */
    public static int getWorldTemperatureSign(
            double worldTemp,
            double minTemp,
            double maxTemp
    )
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

    public static int sign(double value)
    {
        if (value < 0.0)
        {
            return -1;
        }
        if (value > 0.0)
        {
            return 1;
        }
        return 0;
    }

    /**
     * Calculates Cold Sweat's raw core-temperature rate before RATE attribute
     * modifiers and entity-climate multipliers are applied.
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
        int worldTempSign =
                getWorldTemperatureSign(
                        worldTemp,
                        minTemp,
                        maxTemp
                );

        if (worldTempSign == 0)
        {
            return 0.0;
        }

        double clampedWorldTemp =
                Math.max(
                        minTemp,
                        Math.min(maxTemp, worldTemp)
                );

        double difference =
                Math.abs(worldTemp - clampedWorldTemp);

        double changeBy =
                Math.max(
                        (difference / 7.0) * tempRate,
                        Math.abs(tempRate / 50.0)
                ) * worldTempSign;

        if (changeBy < 0)
        {
            return applyDampening(
                    changeBy,
                    coldDampening
            );
        }

        if (changeBy > 0)
        {
            return applyDampening(
                    changeBy,
                    heatDampening
            );
        }

        return 0.0;
    }

    /**
     * Cold Sweat's equilibrium behavior from AbstractTempCap.
     *
     * This returns the amount to add to the newly-calculated core temperature;
     * the caller still performs upstream's "do not fight a CORE modifier"
     * direction check before applying it.
     */
    public static double calculateEquilibriumDelta(
            double coreTemp,
            double storedCoreTemp,
            double worldTemp,
            double minTemp,
            double maxTemp,
            double tempRate,
            double coldDampening,
            double heatDampening,
            boolean peacefulImmunity
    )
    {
        int worldTempSign =
                getWorldTemperatureSign(
                        worldTemp,
                        minTemp,
                        maxTemp
                );

        boolean fullyColdDampened =
                worldTempSign < 0
                        && (coldDampening >= 1.0
                            || peacefulImmunity);

        boolean fullyHeatDampened =
                worldTempSign > 0
                        && (heatDampening >= 1.0
                            || peacefulImmunity);

        int coreTempSign = sign(coreTemp);
        double amount = 0.0;

        if (fullyColdDampened && coreTempSign < 0)
        {
            amount = tempRate / 10.0;
        }
        else if (fullyHeatDampened && coreTempSign > 0)
        {
            amount = tempRate / -10.0;
        }
        else if (coreTempSign != 0
                && coreTempSign != worldTempSign)
        {
            amount =
                    (coreTempSign == 1
                            ? worldTemp - maxTemp
                            : worldTemp - minTemp)
                    / 3.0;
        }

        if (Double.compare(amount, 0.0) == 0)
        {
            return 0.0;
        }

        double changeBy = maxAbs(
                amount * tempRate,
                tempRate / 10.0 * -coreTempSign
        );

        return minAbs(
                changeBy,
                -storedCoreTemp
        );
    }

    /**
     * Cold Sweat's dampening blend for one side of the temperature spectrum.
     */
    public static double applyDampening(
            double change,
            double dampening
    )
    {
        if (dampening < 0)
        {
            return change
                    * (1.0 + Math.abs(dampening));
        }

        double factor =
                Math.max(
                        0.0,
                        Math.min(1.0, dampening)
                );

        return change + (0.0 - change) * factor;
    }

    public static double clamp(
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

    private static double maxAbs(double first, double second)
    {
        return Math.abs(first) >= Math.abs(second)
                ? first
                : second;
    }

    private static double minAbs(double first, double second)
    {
        return Math.abs(first) <= Math.abs(second)
                ? first
                : second;
    }
}
