package com.momosoftworks.coldsweat.common.capability.temperature;

/**
 * Loader-independent core body-temperature runtime math.
 *
 * M7.12h keeps Cold Sweat's normalized CORE/BODY traits, but moves their
 * environmental evolution onto a high-inertia homeostatic model. The legacy
 * method signatures remain because the surrounding runtime and compatibility
 * surface still call them.
 */
public final class TemperatureRuntime
{
    public static final double DEFAULT_FREEZING_POINT = 0.5;
    public static final double DEFAULT_BURNING_POINT = 1.7;
    public static final double DEFAULT_TEMP_RATE = 1.0;

    public static final double NORMAL_BODY_C = 37.0;

    /**
     * A displaced core returns toward 37 C slowly rather than snapping back.
     * 0.45 C/min is deliberately conservative; outward environmental drift is
     * owned by the M7.12h thermoregulation runtime.
     */
    private static final double CORE_RECOVERY_C_PER_MINUTE = 0.45;

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
     * Calculates Cold Sweat's legacy environmental pressure before RATE
     * attributes and armor insulation are applied.
     *
     * M7.12h no longer adds this value directly to CORE. Instead it becomes a
     * compact severity signal consumed by ThermoregulationRuntime.
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
     * High-inertia recovery toward neutral CORE.
     *
     * The environmental caller already prevents equilibrium from fighting an
     * active outward RATE in the opposite direction. That means this method can
     * simply describe the body's slow return toward ~37 C whenever regulation
     * has spare capacity or exposure changes direction.
     *
     * The legacy parameters are intentionally retained for API compatibility.
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
        if (Math.abs(coreTemp) < 1.0e-9)
        {
            return 0.0;
        }

        double currentC = bodyStressToCelsius(coreTemp);

        double maxDeltaCPerTick =
                CORE_RECOVERY_C_PER_MINUTE / (60.0 * 20.0);

        double deltaC =
                clamp(
                        NORMAL_BODY_C - currentC,
                        -maxDeltaCPerTick,
                        maxDeltaCPerTick
                );

        double nextStress =
                celsiusToBodyStress(currentC + deltaC);

        return minAbs(
                nextStress - coreTemp,
                -storedCoreTemp
        );
    }

    /**
     * Canonical presentation/physiology mapping for normalized Cold Sweat body
     * stress. This was originally client-only HUD math; M7.12h promotes it so
     * the server's core inertia and the HUD use exactly the same scale.
     *
     * Anchors:
     *   0 -> 37 C
     * -50 -> 35 C
     * -100 -> 33 C
     * +50 -> 41 C
     * +100 -> 43 C
     */
    public static double bodyStressToCelsius(double bodyStress)
    {
        if (bodyStress <= 0.0)
        {
            return NORMAL_BODY_C + bodyStress * 0.04;
        }

        if (bodyStress <= 50.0)
        {
            return NORMAL_BODY_C + bodyStress * 0.08;
        }

        return 41.0 + (bodyStress - 50.0) * 0.04;
    }

    /**
     * Exact inverse of bodyStressToCelsius.
     */
    public static double celsiusToBodyStress(double celsius)
    {
        if (celsius <= NORMAL_BODY_C)
        {
            return (celsius - NORMAL_BODY_C) / 0.04;
        }

        if (celsius <= 41.0)
        {
            return (celsius - NORMAL_BODY_C) / 0.08;
        }

        return 50.0 + (celsius - 41.0) / 0.04;
    }


    /**
     * Canonical cold impairment progression used by gameplay symptoms.
     *
     * 35 C -> symptoms begin
     * 33 C -> full cold impairment / critical cold threshold neighborhood
     *
     * Using Celsius here keeps symptom staging tied to the physiological model
     * rather than to arbitrary normalized BODY numbers.
     */
    public static double coldImpairmentFactor(double bodyStress)
    {
        double coreC = bodyStressToCelsius(bodyStress);
        return clamp(
                (35.0 - coreC) / 2.0,
                0.0,
                1.0
        );
    }

    /**
     * Shared client/server visual symptom stage.
     *
     * Stage 0: no strong symptom presentation
     * Stage 1: dangerous cold/heat
     * Stage 2: near-critical cold/heat
     */
    public static int bodyVisualEffectLevel(double coreCelsius)
    {
        if (coreCelsius <= 33.5 || coreCelsius >= 41.0)
        {
            return 2;
        }

        if (coreCelsius <= 35.0 || coreCelsius >= 39.5)
        {
            return 1;
        }

        return 0;
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

    private static double minAbs(double first, double second)
    {
        return Math.abs(first) <= Math.abs(second)
                ? first
                : second;
    }
}
