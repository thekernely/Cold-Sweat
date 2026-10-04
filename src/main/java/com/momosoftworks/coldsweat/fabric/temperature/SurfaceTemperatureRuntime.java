package com.momosoftworks.coldsweat.fabric.temperature;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureModifierRuntime;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fast thermal surface layer between the environment and the high-inertia core.
 *
 * Environment/radiation changes surface temperature first, and the surface/core
 * gradient then drives internal heat transfer.
 *
 * M7.12q routed vanilla freezing/powdered snow through this layer instead of
 * subtracting directly from BASE/BODY.
 *
 * M7.12r keeps the HUD honest: there is still only one visible body/core
 * temperature. Extreme radiant heat therefore has to move the real CORE quickly
 * enough to be visible, rather than faking a blended "body" number.
 *
 * M7.12r3 made snow a finite surface-cooling load that competes with radiant
 * heat. M7.12r4 prevented deliberate cold banking below 37 C. M7.12r5 narrows
 * the strong rescue semantics further: powdered snow is always physically cold,
 * but the emergency rescue bonus is only armed by recent acute radiant heat.
 * Hot-biome exposure alone therefore does not turn a snow bucket into a portable
 * air-conditioner.
 */
public final class SurfaceTemperatureRuntime
{
    private static final double HOT_ENVIRONMENT_COUPLING = 0.45;
    private static final double COLD_ENVIRONMENT_COUPLING = 0.35;

    private static final double BASE_WARM_RESPONSE_PER_TICK = 0.018;
    private static final double MAX_RADIANT_WARM_RESPONSE_BONUS = 0.055;
    private static final double COOL_RESPONSE_PER_TICK = 0.045;

    /*
     * Powdered snow is a strong but finite conductive cold load. It no longer
     * overrides every simultaneous heat source or forces the surface to 37 C.
     *
     * The value is intentionally much lower than r2's 0.20 emergency override:
     * snow should cool an escaped overheated player quickly, but sustained
     * bonfire/lava radiation must still be able to win the heat balance.
     */
    private static final double POWDER_SNOW_COOL_RESPONSE_PER_TICK = 0.05;
    private static final double POWDER_SNOW_SURFACE_TARGET_C = 5.0;

    /*
     * In chronic hot climate, snow is mitigation rather than active cooling.
     * Keep a fraction of the hot surface excess instead of dragging surface
     * below CORE. Acute radiant rescue and genuinely cooler surroundings still
     * use the conductive snow-cooling path.
     */
    private static final double CHRONIC_HOT_SNOW_SURFACE_EXCESS_MULTIPLIER = 0.50;

    /*
     * Strong snow rescue is an emergency response to direct radiant heat, not a
     * universal counter to warm climates. A nearby lava source or concentrated
     * campfire load arms the rescue for a short reaction window after escape.
     */
    private static final double ACUTE_RADIANT_RESCUE_LOAD = 3000.0;
    private static final long ACUTE_RADIANT_RESCUE_MEMORY_TICKS = 100L;

    private static final double RADIANT_FAST_RESPONSE_LOAD = 36000.0;

    private static final double FULL_FREEZING_SURFACE_TARGET_C = 5.0;

    private static final double MIN_SURFACE_C = -20.0;
    private static final double MAX_SURFACE_C = 95.0;

    private static final Map<LivingEntity, State> STATES =
            new WeakHashMap<>();

    private static final Map<LivingEntity, Long> LAST_ACUTE_RADIANT_EXPOSURE =
            new WeakHashMap<>();

    private SurfaceTemperatureRuntime()
    {
    }

    public static State updateAndGet(LivingEntity entity)
    {
        long now = entity.level().getGameTime();

        State existing = STATES.get(entity);
        if (existing != null
                && existing.capturedGameTime() == now)
        {
            return existing;
        }

        double coreStress =
                Temperature.get(
                        entity,
                        Temperature.Trait.CORE
                );

        double coreC =
                TemperatureRuntime.bodyStressToCelsius(
                        coreStress
                );

        EnvironmentSnapshot snapshot =
                TemperatureModifierRuntime
                        .getEnvironmentSnapshot(entity)
                        .orElse(null);

        double environmentC =
                snapshot != null
                        ? Temperature.convert(
                                snapshot.effectiveTemperature(),
                                Temperature.Units.MC,
                                Temperature.Units.C,
                                false
                        )
                        : coreC;

        double radiantLoad =
                snapshot != null
                        && snapshot.spatial().available()
                        ? snapshot.spatial().radiantLoad()
                        : 0.0;

        ServerPlayer serverPlayer =
                entity instanceof ServerPlayer player
                        ? player
                        : null;

        boolean powderSnowContact =
                serverPlayer != null
                        && serverPlayer.isInPowderSnow;

        boolean overheatedInPowderSnow =
                powderSnowContact
                        && coreC > TemperatureRuntime.NORMAL_BODY_C;

        if (radiantLoad >= ACUTE_RADIANT_RESCUE_LOAD)
        {
            LAST_ACUTE_RADIANT_EXPOSURE.put(
                    entity,
                    now
            );
        }

        Long lastAcuteRadiantTick =
                LAST_ACUTE_RADIANT_EXPOSURE.get(entity);

        boolean recentAcuteRadiantHeat =
                lastAcuteRadiantTick != null
                        && now >= lastAcuteRadiantTick
                        && now - lastAcuteRadiantTick
                                <= ACUTE_RADIANT_RESCUE_MEMORY_TICKS;

        boolean acutePowderSnowRescue =
                overheatedInPowderSnow
                        && recentAcuteRadiantHeat;

        /*
         * An actually overheated player should not simultaneously receive the
         * vanilla freezing presentation. This suppression is presentation/state
         * hygiene only; it does NOT imply the strong rescue bonus is active.
         * In a merely hot biome, snow only gets the modest conductive path in
         * ThermoregulationRuntime.
         */
        if (overheatedInPowderSnow)
        {
            serverPlayer.setTicksFrozen(0);
        }

        double freezingProgress =
                overheatedInPowderSnow
                        ? 0.0
                        : getFreezingProgress(entity);

        double coupling =
                environmentC >= coreC
                        ? HOT_ENVIRONMENT_COUPLING
                        : COLD_ENVIRONMENT_COUPLING;

        double targetSurfaceC =
                coreC
                        + (environmentC - coreC)
                        * coupling;

        /*
         * Generic vanilla-freezing pressure.
         */
        if (freezingProgress > 0.0)
        {
            double freezingTargetC =
                    lerp(
                            coreC,
                            FULL_FREEZING_SURFACE_TARGET_C,
                            freezingProgress
                    );

            targetSurfaceC =
                    Math.min(
                            targetSurfaceC,
                            freezingTargetC
                    );
        }

        targetSurfaceC =
                clamp(
                        targetSurfaceC,
                        MIN_SURFACE_C,
                        MAX_SURFACE_C
                );

        double currentSurfaceC =
                existing != null
                        ? existing.surfaceCelsius()
                        : baselineSurfaceC(
                                coreC,
                                environmentC
                        );

        double radiantResponse =
                clamp01(
                        radiantLoad
                                / RADIANT_FAST_RESPONSE_LOAD
                );

        double response =
                targetSurfaceC > currentSurfaceC
                        ? BASE_WARM_RESPONSE_PER_TICK
                                + MAX_RADIANT_WARM_RESPONSE_BONUS
                                * radiantResponse
                        : COOL_RESPONSE_PER_TICK;

        /*
         * First apply ordinary environment/radiation exchange. Powdered snow
         * then adds its own finite conductive cooling flux instead of replacing
         * the environment target. This makes simultaneous hot and cold sources
         * actually compete: snow is excellent after escaping the heat, but it
         * cannot create a permanent safe bubble inside six campfires.
         */
        double nextSurfaceC =
                approach(
                        currentSurfaceC,
                        targetSurfaceC,
                        response
                );

        if (overheatedInPowderSnow)
        {
            boolean chronicHotClimate =
                    !acutePowderSnowRescue
                            && environmentC > coreC;

            if (chronicHotClimate)
            {
                /*
                 * Snow may blunt a hotter-than-core environment, but it must
                 * not make the simulated surface colder than CORE and thereby
                 * manufacture active cooling in a hot biome.
                 */
                double hotSurfaceExcess =
                        Math.max(
                                0.0,
                                nextSurfaceC - coreC
                        );

                nextSurfaceC =
                        coreC
                                + hotSurfaceExcess
                                * CHRONIC_HOT_SNOW_SURFACE_EXCESS_MULTIPLIER;
            }
            else if (currentSurfaceC > POWDER_SNOW_SURFACE_TARGET_C)
            {
                nextSurfaceC +=
                        (POWDER_SNOW_SURFACE_TARGET_C - currentSurfaceC)
                                * POWDER_SNOW_COOL_RESPONSE_PER_TICK;
            }
        }

        nextSurfaceC =
                clamp(
                        nextSurfaceC,
                        MIN_SURFACE_C,
                        MAX_SURFACE_C
                );

        State state =
                new State(
                        nextSurfaceC,
                        targetSurfaceC,
                        environmentC,
                        radiantLoad,
                        freezingProgress,
                        powderSnowContact,
                        acutePowderSnowRescue,
                        now
                );

        STATES.put(
                entity,
                state
        );

        return state;
    }

    public static State getState(LivingEntity entity)
    {
        return updateAndGet(entity);
    }

    public static double getSurfaceCelsius(LivingEntity entity)
    {
        return updateAndGet(entity)
                .surfaceCelsius();
    }

    public static void remove(LivingEntity entity)
    {
        STATES.remove(entity);
        LAST_ACUTE_RADIANT_EXPOSURE.remove(entity);
    }

    private static double getFreezingProgress(
            LivingEntity entity
    )
    {
        int requiredTicks =
                Math.max(
                        1,
                        entity.getTicksRequiredToFreeze()
                );

        return clamp01(
                entity.getTicksFrozen()
                        / (double) requiredTicks
        );
    }

    private static double baselineSurfaceC(
            double coreC,
            double environmentC
    )
    {
        double normalOffset =
                environmentC < coreC
                        ? -3.0
                        : -1.0;

        return clamp(
                coreC + normalOffset,
                MIN_SURFACE_C,
                MAX_SURFACE_C
        );
    }

    private static double approach(
            double current,
            double target,
            double response
    )
    {
        double clampedResponse =
                clamp01(response);

        double next =
                current
                        + (target - current)
                        * clampedResponse;

        return Math.abs(target - next) < 1.0e-7
                ? target
                : next;
    }

    private static double lerp(
            double start,
            double end,
            double delta
    )
    {
        double t = clamp01(delta);
        return start + (end - start) * t;
    }

    private static double clamp01(double value)
    {
        return clamp(
                value,
                0.0,
                1.0
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

    public record State(
            double surfaceCelsius,
            double targetSurfaceCelsius,
            double environmentCelsius,
            double radiantLoad,
            double freezingProgress,
            boolean powderSnowContact,
            boolean acutePowderSnowRescue,
            long capturedGameTime
    )
    {
    }
}
