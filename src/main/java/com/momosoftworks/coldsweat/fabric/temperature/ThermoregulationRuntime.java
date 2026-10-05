package com.momosoftworks.coldsweat.fabric.temperature;

import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.WaterTempModifier;
import com.momosoftworks.coldsweat.api.util.Hydration;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureModifierRuntime;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * M7 high-inertia thermoregulation layer.
 *
 * Ordinary weather still uses demand -> regulation -> residual load -> slow
 * CORE drift.
 *
 * Extreme local heat is different: a hot surface creates a real surface/core
 * gradient and that gradient moves the actual CORE quickly enough to be visible
 * on the existing two-reading HUD. No fake/blended body temperature is needed.
 *
 * Powdered snow always provides some conductive cooling while the player is
 * overheated, but its strong emergency-rescue bonus is only armed by recent
 * acute radiant heat. That keeps snow useful after lava/fire mistakes without
 * making "carry one snow bucket" the universal answer to deserts and other
 * prolonged hot climates. The rescue still fades toward 37 C and cannot bank
 * hypothermia below normal.
 */
public final class ThermoregulationRuntime
{
    private static final double COLD_DEMAND_RATE_UNIT = 0.080;
    private static final double HEAT_DEMAND_RATE_UNIT = 0.018;

    private static final double BASELINE_REGULATION_CAPACITY = 1.0;

    private static final double FULL_WET_COLD_DEMAND_BONUS = 0.75;

    /*
     * Cold is already gameplay-locked; keep its weather-scale physiology
     * unchanged. Heat gets its own chronic-climate curve instead of sharing the
     * cold constants. Any environment above the configured burning point must
     * eventually be able to overwhelm regulation, while near-threshold heat
     * remains gradual.
     *
     * At ~47-48 C apparent environment, an unprotected player should now gain
     * core heat on a several-minute gameplay scale rather than effectively
     * indefinitely. More extreme climates accelerate further.
     */
    private static final double COLD_DRIFT_C_PER_MINUTE_PER_RESIDUAL = 0.35;
    private static final double MAX_COLD_DRIFT_C_PER_MINUTE = 0.75;

    private static final double HEAT_DRIFT_C_PER_MINUTE_PER_RESIDUAL = 0.50;
    private static final double MAX_HEAT_DRIFT_C_PER_MINUTE = 1.25;

    /*
     * Acute surface -> CORE heat transfer.
     *
     * This is intentionally more gameplay-forward than the weather-scale model.
     * A small surface/core gap still does essentially nothing, but once the
     * player's surface becomes genuinely hot the transfer accelerates
     * quadratically with the gap.
     *
     * That gives the requested proximity/time behavior naturally:
     * closer/stronger radiation -> hotter surface -> larger gradient -> faster
     * real CORE rise. There is still a hard catastrophic ceiling so pathological
     * source stacking cannot move CORE without bound.
     *
     * Ordinary climate remains on the slow demand/regulation path.
     */
    private static final double FAST_HEAT_GRADIENT_START_C = 2.0;
    private static final double FAST_HEAT_TRANSFER_CURVE = 0.030;
    private static final double MAX_SURFACE_CORE_HEAT_TRANSFER_C_PER_MINUTE = 36.0;

    /*
     * Generic vanilla-freezing coupling retained from M7.12q. This remains
     * intentionally slower and is not used as the emergency overheat rescue.
     */
    private static final double FREEZING_COLD_GRADIENT_START_C = 4.0;
    private static final double FREEZING_COLD_TRANSFER_C_PER_MINUTE_PER_C = 0.10;
    private static final double MAX_FREEZING_CORE_COOLING_C_PER_MINUTE = 2.5;

    /*
     * Direct powdered-snow contact gets an acute rescue bonus only while CORE is
     * above normal. The bonus is still a finite competing heat flux (not an
     * override), but it fades through the final 2 C above normal and may never
     * carry CORE below 37 C. Once normal is reached, vanilla frozen-tick / cold
     * physiology owns any further cooling. This prevents deliberate pre-cooling
     * from becoming a free hyperthermia battery.
     */
    private static final double POWDER_SNOW_CORE_GRADIENT_START_C = 1.0;

    /*
     * Mild conductive help available in any hot environment. This is capped
     * deliberately low so powdered snow cannot replace shade, hydration and
     * proper heat protection during prolonged climate exposure.
     */
    private static final double POWDER_SNOW_BASE_COOLING_C_PER_MINUTE_PER_C = 0.05;
    private static final double MAX_POWDER_SNOW_BASE_COOLING_C_PER_MINUTE = 0.50;

    /*
     * Outside the acute post-radiant rescue window, powdered snow is mitigation
     * rather than portable climate immunity. In a chronically hot environment
     * it may halve weather-scale heat gain, but it may not turn that heat load
     * into net active cooling. The player still has to solve the climate with
     * shade, hydration, insulation and/or shelter.
     */
    private static final double CHRONIC_HEAT_SNOW_DRIFT_MULTIPLIER = 0.50;

    /*
     * Strong emergency bonus, only when SurfaceTemperatureRuntime says recent
     * acute radiant exposure armed the rescue window.
     */
    private static final double POWDER_SNOW_RESCUE_TRANSFER_C_PER_MINUTE_PER_C = 0.75;
    private static final double MAX_POWDER_SNOW_RESCUE_COOLING_C_PER_MINUTE = 18.0;
    private static final double POWDER_SNOW_RESCUE_TAPER_C = 2.0;

    private static final long WETNESS_CACHE_TICKS = 5L;

    private static final Map<LivingEntity, WetnessSample> WETNESS_CACHE =
            new WeakHashMap<>();

    private static final Map<LivingEntity, State> LAST_STATE =
            new WeakHashMap<>();

    private ThermoregulationRuntime()
    {
    }

    public static double applyEnvironmentalRate(
            LivingEntity entity,
            double legacyRate
    )
    {
        State state =
                stateFromRate(
                        entity,
                        legacyRate
                );

        LAST_STATE.put(
                entity,
                state
        );

        SurfaceTemperatureRuntime.State surfaceState =
                SurfaceTemperatureRuntime
                        .updateAndGet(entity);

        double environmentalDriftCPerMinute =
                environmentalDriftCPerMinute(
                        state,
                        surfaceState
                );

        if (Math.abs(environmentalDriftCPerMinute) < 1.0e-12)
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

        double nextStress =
                TemperatureRuntime.celsiusToBodyStress(
                        coreC
                                + environmentalDriftCPerMinute
                                / (60.0 * 20.0)
                );

        return nextStress - storedCore;
    }

    /**
     * Apply direct surface/core heat exchange independently from the legacy
     * environmental RATE gate.
     *
     * This distinction matters for two reasons:
     * - a cold surface must keep exchanging heat with CORE even when WORLD is
     *   inside the normal habitable range;
     * - once the simulated surface itself is already hot/cold, armor insulation
     *   must not be applied a second time to the internal surface/core gradient.
     *
     * Ordinary climate still flows through RATE -> thermoregulation -> armor.
     */
    public static double applySurfaceCoreTransfer(
            LivingEntity entity,
            double currentCoreStress
    )
    {
        double coreC =
                TemperatureRuntime.bodyStressToCelsius(
                        currentCoreStress
                );

        SurfaceTemperatureRuntime.State surfaceState =
                SurfaceTemperatureRuntime
                        .updateAndGet(entity);

        double transferCPerMinute =
                surfaceCoreTransferCPerMinute(
                        coreC,
                        surfaceState
                );

        if (Math.abs(transferCPerMinute) < 1.0e-12)
        {
            return 0.0;
        }

        double nextCoreC =
                coreC
                        + transferCPerMinute
                        / (60.0 * 20.0);

        /*
         * The acute powdered-snow rescue bonus must never create stored cold
         * below normal. It buys the player back toward homeostasis; remaining in
         * snow after that point transitions to ordinary freezing physiology.
         */
        if (surfaceState.powderSnowContact()
                && coreC > TemperatureRuntime.NORMAL_BODY_C
                && nextCoreC < TemperatureRuntime.NORMAL_BODY_C)
        {
            nextCoreC = TemperatureRuntime.NORMAL_BODY_C;
        }

        double nextStress =
                TemperatureRuntime.celsiusToBodyStress(
                        nextCoreC
                );

        return nextStress - currentCoreStress;
    }

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

        /*
         * M8.8 hydration coupling:
         * dehydration primarily impairs heat shedding, not cold
         * thermogenesis. At >=5 hydration capacity is unchanged. From
         * 5 -> 0 it falls smoothly from 100% -> 40%.
         */
        double capacity =
                BASELINE_REGULATION_CAPACITY
                        * hydrationHeatRegulationFactor(
                                entity,
                                hot
                        );

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

        SurfaceTemperatureRuntime.State surfaceState =
                SurfaceTemperatureRuntime
                        .updateAndGet(entity);

        double coreCelsius =
                TemperatureRuntime.bodyStressToCelsius(
                        Temperature.get(
                                entity,
                                Temperature.Trait.CORE
                        )
                );

        State provisional =
                new State(
                        coldDemand,
                        heatDemand,
                        capacity,
                        regulatoryLoad,
                        residualLoad,
                        wetness,
                        coreCelsius,
                        surfaceState.surfaceCelsius(),
                        surfaceState.freezingProgress(),
                        surfaceState.powderSnowContact(),
                        0.0,
                        entity.level().getGameTime()
                );

        double driftCPerMinute =
                environmentalDriftCPerMinute(
                        provisional,
                        surfaceState
                )
                        + surfaceCoreTransferCPerMinute(
                                coreCelsius,
                                surfaceState
                        );

        return new State(
                coldDemand,
                heatDemand,
                capacity,
                regulatoryLoad,
                residualLoad,
                wetness,
                coreCelsius,
                surfaceState.surfaceCelsius(),
                surfaceState.freezingProgress(),
                surfaceState.powderSnowContact(),
                driftCPerMinute,
                entity.level().getGameTime()
        );
    }

    private static double environmentalDriftCPerMinute(
            State state,
            SurfaceTemperatureRuntime.State surfaceState
    )
    {
        double driftCPerMinute =
                normalDriftCPerMinute(state);

        if (driftCPerMinute > 0.0
                && surfaceState.powderSnowContact()
                && !surfaceState.acutePowderSnowRescue())
        {
            driftCPerMinute *=
                    CHRONIC_HEAT_SNOW_DRIFT_MULTIPLIER;
        }

        return driftCPerMinute;
    }

    private static double normalDriftCPerMinute(
            State state
    )
    {
        if (state.coldDemand() > 0.0)
        {
            double coldDriftMagnitudeCPerMinute =
                    Math.min(
                            MAX_COLD_DRIFT_C_PER_MINUTE,
                            state.residualLoad()
                                    * COLD_DRIFT_C_PER_MINUTE_PER_RESIDUAL
                    );

            return -coldDriftMagnitudeCPerMinute;
        }

        if (state.heatDemand() > 0.0)
        {
            double heatDriftMagnitudeCPerMinute =
                    Math.min(
                            MAX_HEAT_DRIFT_C_PER_MINUTE,
                            state.residualLoad()
                                    * HEAT_DRIFT_C_PER_MINUTE_PER_RESIDUAL
                    );

            return heatDriftMagnitudeCPerMinute;
        }

        return 0.0;
    }

    private static double surfaceCoreTransferCPerMinute(
            double coreCelsius,
            SurfaceTemperatureRuntime.State surfaceState
    )
    {
        double heatGradient =
                surfaceState.surfaceCelsius()
                        - coreCelsius;

        double heatExcess =
                Math.max(
                        0.0,
                        heatGradient
                                - FAST_HEAT_GRADIENT_START_C
                );

        double fastHeatTransferCPerMinute =
                Math.min(
                        MAX_SURFACE_CORE_HEAT_TRANSFER_C_PER_MINUTE,
                        FAST_HEAT_TRANSFER_CURVE
                                * heatExcess
                                * heatExcess
                );

        double coldGradient =
                coreCelsius
                        - surfaceState.surfaceCelsius();

        boolean overheatedInPowderSnow =
                surfaceState.powderSnowContact()
                        && coreCelsius > TemperatureRuntime.NORMAL_BODY_C;

        double freezingColdTransferCPerMinute =
                overheatedInPowderSnow
                        ? 0.0
                        : Math.min(
                                MAX_FREEZING_CORE_COOLING_C_PER_MINUTE,
                                Math.max(
                                        0.0,
                                        coldGradient
                                                - FREEZING_COLD_GRADIENT_START_C
                                )
                                        * FREEZING_COLD_TRANSFER_C_PER_MINUTE_PER_C
                                        * surfaceState.freezingProgress()
                        );

        double powderSnowContactCoolingCPerMinute = 0.0;

        if (overheatedInPowderSnow)
        {
            double conductiveGradient =
                    Math.max(
                            0.0,
                            coldGradient
                                    - POWDER_SNOW_CORE_GRADIENT_START_C
                    );

            boolean chronicHotClimate =
                    !surfaceState.acutePowderSnowRescue()
                            && surfaceState.environmentCelsius() > coreCelsius;

            /*
             * Mild direct snow cooling is allowed after acute radiant exposure
             * or when the surroundings are actually cooler than CORE. In a
             * hotter-than-core chronic climate, snow is mitigation only: no
             * negative core flux is manufactured here.
             */
            if (!chronicHotClimate)
            {
                powderSnowContactCoolingCPerMinute =
                        Math.min(
                                MAX_POWDER_SNOW_BASE_COOLING_C_PER_MINUTE,
                                conductiveGradient
                                        * POWDER_SNOW_BASE_COOLING_C_PER_MINUTE_PER_C
                        );
            }

            /*
             * The strong rescue is reserved for the short post-radiant window:
             * lava, concentrated campfires, etc. It tapers away near 37 C.
             */
            if (surfaceState.acutePowderSnowRescue())
            {
                double rescueTaper =
                        clamp01(
                                (coreCelsius - TemperatureRuntime.NORMAL_BODY_C)
                                        / POWDER_SNOW_RESCUE_TAPER_C
                        );

                powderSnowContactCoolingCPerMinute +=
                        Math.min(
                                MAX_POWDER_SNOW_RESCUE_COOLING_C_PER_MINUTE,
                                conductiveGradient
                                        * POWDER_SNOW_RESCUE_TRANSFER_C_PER_MINUTE_PER_C
                        )
                                * rescueTaper;
            }
        }

        double netSurfaceTransferCPerMinute =
                fastHeatTransferCPerMinute
                        - freezingColdTransferCPerMinute
                        - powderSnowContactCoolingCPerMinute;

        /*
         * Chronic hot-climate rule:
         *
         * Powdered snow may slow a hot biome down, but outside the explicitly
         * armed acute-radiant rescue window it cannot make a >37 C environment
         * cool an overheated player. This closes the "stand in one snow block
         * forever in a 48 C badlands" loophole while preserving:
         *
         * - strong rescue after lava / concentrated radiant heat;
         * - ordinary snow cooling in neutral or cold surroundings;
         * - normal freezing/hypothermia once the player is no longer overheated.
         */
        boolean chronicHotSnowMitigation =
                overheatedInPowderSnow
                        && !surfaceState.acutePowderSnowRescue()
                        && surfaceState.environmentCelsius()
                                > coreCelsius;

        if (chronicHotSnowMitigation)
        {
            netSurfaceTransferCPerMinute =
                    Math.max(
                            0.0,
                            netSurfaceTransferCPerMinute
                    );
        }

        return netSurfaceTransferCPerMinute;
    }

    /**
     * Prevent neutral homeostatic recovery from becoming a hidden cooling path
     * while powdered snow is merely mitigating a hotter-than-core climate.
     * Acute radiant rescue is intentionally exempt.
     */
    public static boolean suppressEquilibriumCoolingInChronicHotSnow(
            LivingEntity entity,
            double currentCoreStress
    )
    {
        double coreCelsius =
                TemperatureRuntime.bodyStressToCelsius(
                        currentCoreStress
                );

        if (coreCelsius <= TemperatureRuntime.NORMAL_BODY_C)
        {
            return false;
        }

        SurfaceTemperatureRuntime.State surfaceState =
                SurfaceTemperatureRuntime
                        .updateAndGet(entity);

        return surfaceState.powderSnowContact()
                && !surfaceState.acutePowderSnowRescue()
                && surfaceState.environmentCelsius() > coreCelsius;
    }

    private static double hydrationHeatRegulationFactor(
            LivingEntity entity,
            boolean hot
    )
    {
        if (!hot
                || !(entity instanceof Player player)
                || player.isCreative()
                || player.isSpectator())
        {
            return 1.0;
        }

        double hydration =
                Math.max(
                        0.0,
                        Math.min(
                                5.0,
                                Hydration.get(player)
                        )
                );

        if (hydration >= 5.0)
        {
            return 1.0;
        }

        return 0.40
                + 0.60
                * (hydration / 5.0);
    }

    private static double clamp01(double value)
    {
        return Math.max(
                0.0,
                Math.min(1.0, value)
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

    public record State(
            double coldDemand,
            double heatDemand,
            double regulationCapacity,
            double regulatoryLoad,
            double residualLoad,
            double wetness,
            double coreCelsius,
            double surfaceCelsius,
            double freezingProgress,
            boolean powderSnowContact,
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
