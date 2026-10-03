package com.momosoftworks.coldsweat.fabric.temperature;

import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.WaterTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureModifierRuntime;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureRuntime;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * M7.12h high-inertia thermoregulation layer.
 *
 * The legacy Cold Sweat environmental RATE is no longer interpreted as "move
 * CORE by this much right now". Instead it becomes thermoregulatory demand:
 *
 * effective environment -> demand -> baseline regulation -> residual load
 * -> slow physical core drift
 *
 * M8 will replace the fixed baseline regulation capacity with calorie- and
 * hydration-backed capacity. The State record deliberately exposes those hooks
 * now so M8 does not need to redesign M7's temperature pipeline again.
 */
public final class ThermoregulationRuntime
{
    /*
     * Legacy RATE magnitudes corresponding to a normalized regulatory demand
     * of 1.0. Cold gets slightly more baseline regulatory headroom than heat.
     *
     * With the current Cold Sweat pressure curve this means a dry player can
     * regulate roughly ~14 C below the cold threshold before core drift starts.
     * Heat begins exceeding regulation at a somewhat smaller margin.
     */
    private static final double COLD_DEMAND_RATE_UNIT = 0.080;
    private static final double HEAT_DEMAND_RATE_UNIT = 0.060;

    private static final double BASELINE_REGULATION_CAPACITY = 1.0;

    /*
     * Full rain/water saturation raises cold-side demand by 75%. Wetness no
     * longer lies to WORLD by subtracting several degrees from the air; it now
     * acts where it belongs - on body heat-loss pressure.
     */
    private static final double FULL_WET_COLD_DEMAND_BONUS = 0.75;

    /*
     * Once regulation is exceeded, physical core drift remains intentionally
     * slow. A residual load of 1.0 produces about 0.35 C/minute of drift, with
     * an extreme cap of 0.75 C/minute.
     */
    private static final double DRIFT_C_PER_MINUTE_PER_RESIDUAL = 0.35;
    private static final double MAX_DRIFT_C_PER_MINUTE = 0.75;

    private static final long WETNESS_CACHE_TICKS = 5L;

    private static final Map<LivingEntity, WetnessSample> WETNESS_CACHE =
            new WeakHashMap<>();

    private static final Map<LivingEntity, State> LAST_STATE =
            new WeakHashMap<>();

    private ThermoregulationRuntime()
    {
    }

    /**
     * Transform the already attribute-resolved legacy environmental RATE into
     * a slow CORE delta. Armor insulation is applied immediately after this
     * method by ArmorInsulationRuntime, preserving the existing equipment
     * system as the final heat-transfer reduction layer.
     */
    public static double applyEnvironmentalRate(
            LivingEntity entity,
            double legacyRate
    )
    {
        if (Math.abs(legacyRate) < 1.0e-12)
        {
            LAST_STATE.put(
                    entity,
                    stateFromRate(entity, 0.0)
            );
            return 0.0;
        }

        State state =
                stateFromRate(
                        entity,
                        legacyRate
                );

        LAST_STATE.put(entity, state);

        if (state.residualLoad() <= 0.0)
        {
            return 0.0;
        }

        double storedCore =
                Temperature.get(
                        entity,
                        Temperature.Trait.CORE
                );

        double coreC =
                TemperatureRuntime.bodyStressToCelsius(
                        storedCore
                );

        double deltaC =
                state.driftCPerMinute()
                        / (60.0 * 20.0);

        double nextStress =
                TemperatureRuntime.celsiusToBodyStress(
                        coreC + deltaC
                );

        return nextStress - storedCore;
    }

    /**
     * Current physiology-facing thermoregulation state.
     *
     * If the live RATE transform already ran this game tick, return that exact
     * state. Otherwise estimate from the synchronized current environment. This
     * keeps the M8 hook meaningful even while the player is fully comfortable
     * and no environmental RATE is being resolved.
     */
    public static State getState(LivingEntity entity)
    {
        long now = entity.level().getGameTime();

        State last = LAST_STATE.get(entity);
        if (last != null
                && last.capturedGameTime() == now)
        {
            return last;
        }

        double world =
                Temperature.get(
                        entity,
                        Temperature.Trait.WORLD
                );

        double freezingPoint =
                EntityTempManager.resolveAttributeValue(
                        entity,
                        Temperature.Trait.FREEZING_POINT,
                        TemperatureRuntime.DEFAULT_FREEZING_POINT
                );

        double burningPoint =
                EntityTempManager.resolveAttributeValue(
                        entity,
                        Temperature.Trait.BURNING_POINT,
                        TemperatureRuntime.DEFAULT_BURNING_POINT
                );

        double coldDampening =
                EntityTempManager.resolveAttributeValue(
                        entity,
                        Temperature.Trait.COLD_DAMPENING,
                        0.0
                );

        double heatDampening =
                EntityTempManager.resolveAttributeValue(
                        entity,
                        Temperature.Trait.HEAT_DAMPENING,
                        0.0
                );

        double rawRate =
                TemperatureRuntime.calculateTemperatureRate(
                        world,
                        freezingPoint,
                        burningPoint,
                        TemperatureRuntime.DEFAULT_TEMP_RATE,
                        coldDampening,
                        heatDampening
                );

        return stateFromRate(
                entity,
                rawRate
        );
    }

    private static State stateFromRate(
            LivingEntity entity,
            double rate
    )
    {
        double wetness =
                getWetnessFraction(entity);

        boolean cold = rate < 0.0;
        boolean hot = rate > 0.0;

        double demand = 0.0;

        if (cold)
        {
            demand =
                    Math.abs(rate)
                            / COLD_DEMAND_RATE_UNIT;

            demand *=
                    1.0
                            + wetness
                            * FULL_WET_COLD_DEMAND_BONUS;
        }
        else if (hot)
        {
            demand =
                    Math.abs(rate)
                            / HEAT_DEMAND_RATE_UNIT;
        }

        double coldDemand =
                cold ? demand : 0.0;

        double heatDemand =
                hot ? demand : 0.0;

        double capacity =
                BASELINE_REGULATION_CAPACITY;

        double regulatoryLoad =
                capacity > 0.0
                        ? demand / capacity
                        : demand > 0.0
                                ? Double.POSITIVE_INFINITY
                                : 0.0;

        double residualLoad =
                Math.max(
                        0.0,
                        demand - capacity
                );

        double driftMagnitudeCPerMinute =
                Math.min(
                        MAX_DRIFT_C_PER_MINUTE,
                        residualLoad
                                * DRIFT_C_PER_MINUTE_PER_RESIDUAL
                );

        double driftCPerMinute =
                cold
                        ? -driftMagnitudeCPerMinute
                        : hot
                                ? driftMagnitudeCPerMinute
                                : 0.0;

        return new State(
                coldDemand,
                heatDemand,
                capacity,
                regulatoryLoad,
                residualLoad,
                wetness,
                TemperatureRuntime.bodyStressToCelsius(
                        Temperature.get(
                                entity,
                                Temperature.Trait.CORE
                        )
                ),
                driftCPerMinute,
                entity.level().getGameTime()
        );
    }

    private static double getWetnessFraction(
            LivingEntity entity
    )
    {
        long now = entity.level().getGameTime();

        WetnessSample cached =
                WETNESS_CACHE.get(entity);

        if (cached != null
                && now >= cached.capturedGameTime()
                && now - cached.capturedGameTime()
                        < WETNESS_CACHE_TICKS)
        {
            return cached.value();
        }

        double wetness = 0.0;

        for (TempModifier modifier :
                TemperatureModifierRuntime.getWorldModifiers(entity))
        {
            if (modifier instanceof WaterTempModifier water)
            {
                wetness =
                        Math.max(
                                wetness,
                                water.getWetnessFraction(entity)
                        );
            }
        }

        wetness =
                Math.max(
                        0.0,
                        Math.min(1.0, wetness)
                );

        WETNESS_CACHE.put(
                entity,
                new WetnessSample(
                        wetness,
                        now
                )
        );

        return wetness;
    }

    /**
     * M8-facing state contract.
     *
     * coldDemand / heatDemand:
     *   physiological demand before resource depletion is introduced.
     *
     * regulationCapacity:
     *   fixed at 1.0 in M7. M8 will make this resource-backed.
     *
     * regulatoryLoad:
     *   demand / capacity. >1 means regulation is being exceeded.
     *
     * residualLoad:
     *   unregulated demand that is actually moving core temperature.
     */
    public record State(
            double coldDemand,
            double heatDemand,
            double regulationCapacity,
            double regulatoryLoad,
            double residualLoad,
            double wetness,
            double coreCelsius,
            double driftCPerMinute,
            long capturedGameTime
    )
    {
    }

    private record WetnessSample(
            double value,
            long capturedGameTime
    )
    {
    }
}
