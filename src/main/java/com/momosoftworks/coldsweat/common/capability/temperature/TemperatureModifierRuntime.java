package com.momosoftworks.coldsweat.common.capability.temperature;

import com.momosoftworks.coldsweat.api.registry.TempModifierRegistry;
import com.momosoftworks.coldsweat.api.temperature.modifier.FreezingTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.WaterTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import com.momosoftworks.coldsweat.fabric.temperature.EnvironmentSnapshot;
import com.momosoftworks.coldsweat.fabric.temperature.EnvironmentSnapshotScanner;
import com.momosoftworks.coldsweat.fabric.temperature.ThermalEnvironment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side runtime owner for environmental TempModifiers.
 *
 * M7.12 keeps Cold Sweat's modifier identity, but the live WORLD calculation
 * is now evaluated in explicit thermal stages:
 *
 * ambient climate -> local/radiant sources -> direct exposure -> WORLD
 *
 * This first split is deliberately behavior-preserving. Existing shade/
 * overcast remains in the ambient-climate stage because it was already part
 * of the pre-local-source chain. True shelter/wind/greenhouse semantics are
 * layered later without forcing block/entity scans to run twice.
 */
public final class TemperatureModifierRuntime
{
    /*
     * Local radiant/source load should respond quickly enough to feel live,
     * but not teleport the apparent environment several degrees in one tick.
     * 0.20 reaches ~99% of a new source load in about one second at 20 TPS.
     */
    /*
     * Ambient climate should not visibly jump as biome/shade/elevation
     * modifiers refresh. This is intentionally lighter/faster than player
     * core inertia: the surroundings HUD should still react within about a
     * second, just without staircase transitions.
     */
    private static final double AMBIENT_RESPONSE_PER_TICK = 0.12;

    private static final double LOCAL_SOURCE_RESPONSE_PER_TICK = 0.20;

    private static final int SPATIAL_SCAN_INTERVAL_TICKS = 16;

    private static final Map<LivingEntity, WorldModifierStages> WORLD_MODIFIERS =
            new IdentityHashMap<>();

    private static final Map<LivingEntity, List<TempModifier>> BASE_MODIFIERS =
            new IdentityHashMap<>();

    /*
     * M7.12f-a promotes the environment cache from a thermal-only breakdown to
     * a future-facing snapshot object. For this foundation slice, the snapshot
     * mirrors the existing live pipeline exactly; spatial fields are explicitly
     * marked unavailable until the unified scan is introduced.
     */
    private static final Map<LivingEntity, EnvironmentSnapshot> ENVIRONMENT_SNAPSHOTS =
            new IdentityHashMap<>();

    private static boolean initialized;

    public static void initialize()
    {
        if (initialized)
        {
            return;
        }
        initialized = true;

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) ->
        {
            if (entity instanceof LivingEntity living
                    && EntityTempManager.isTemperatureEnabled(living))
            {
                installDefaultWorldModifiers(living);
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) ->
        {
            if (entity instanceof LivingEntity living)
            {
                removeEntity(living);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> tickAll());

        ColdSweatFabric.LOGGER.info(
                "Initializing Cold Sweat environmental temperature modifier runtime."
        );
    }

    /**
     * Standalone player WORLD chain split into explicit semantic stages.
     *
     * M7.12e refreshes the normal environmental snapshot on one shared
     * 16-tick cadence, inspired by Homeostatic's environment update model.
     * This avoids independent biome/shade/elevation/source modifiers changing
     * on different frames and producing a visibly "busy" surroundings value.
     *
     * The ordering remains equivalent to the previously-live chain:
     *
     * Biome -> Shade -> Elevation -> Cave Biomes
     *       -> Blocks -> Entities
     *       -> dynamic Water exposure
     *
     * No modifier is evaluated more than once per WORLD update.
     */
    public static void installDefaultWorldModifiers(LivingEntity entity)
    {
        removeEntity(entity);

        WorldModifierStages stages = new WorldModifierStages();

        addWorldModifier(
                entity,
                stages.ambient,
                createRegistered("biomes").tickRate(16)
        );
        addWorldModifier(
                entity,
                stages.ambient,
                createRegistered("shade").tickRate(16)
        );
        addWorldModifier(
                entity,
                stages.ambient,
                createRegistered("elevation").tickRate(16)
        );
        addWorldModifier(
                entity,
                stages.ambient,
                createRegistered("cave_biomes").tickRate(16)
        );

        addWorldModifier(
                entity,
                stages.localSources,
                createRegistered("blocks").tickRate(16)
        );
        addWorldModifier(
                entity,
                stages.localSources,
                createRegistered("entities").tickRate(16)
        );

        WORLD_MODIFIERS.put(entity, stages);
        BASE_MODIFIERS.put(entity, new ArrayList<>());
    }

    /**
     * Compatibility/debug view of every WORLD modifier in live application
     * order. The returned list is detached so callers cannot mutate stage
     * ownership accidentally.
     */
    public static List<TempModifier> getWorldModifiers(LivingEntity entity)
    {
        WorldModifierStages stages = WORLD_MODIFIERS.get(entity);
        if (stages == null)
        {
            return List.of();
        }

        ArrayList<TempModifier> modifiers = new ArrayList<>(
                stages.ambient.size()
                        + stages.localSources.size()
                        + stages.exposure.size()
        );
        modifiers.addAll(stages.ambient);
        modifiers.addAll(stages.localSources);
        modifiers.addAll(stages.exposure);
        return List.copyOf(modifiers);
    }

    public static List<TempModifier> getBaseModifiers(LivingEntity entity)
    {
        return BASE_MODIFIERS.getOrDefault(entity, List.of());
    }

    /**
     * Canonical cached environment snapshot for later M7/M8 systems.
     *
     * M7.12f-a is intentionally behavior-neutral: only the thermal fields are
     * populated from the current modifier pipeline. Unified spatial data is
     * added by the next migration slice.
     */
    public static Optional<EnvironmentSnapshot> getEnvironmentSnapshot(
            LivingEntity entity
    )
    {
        return Optional.ofNullable(
                ENVIRONMENT_SNAPSHOTS.get(entity)
        );
    }

    /**
     * Compatibility view retained while callers migrate to EnvironmentSnapshot.
     */
    public static Optional<ThermalEnvironment> getThermalEnvironment(
            LivingEntity entity
    )
    {
        return getEnvironmentSnapshot(entity)
                .map(EnvironmentSnapshot::thermalEnvironment);
    }

    private static void addWorldModifier(
            LivingEntity entity,
            List<TempModifier> stage,
            TempModifier modifier
    )
    {
        modifier.onAdded(
                entity,
                Temperature.Trait.WORLD
        );
        stage.add(modifier);
    }

    private static TempModifier createRegistered(String path)
    {
        return TempModifierRegistry.getValue(ColdSweatFabric.id(path))
                .orElseThrow(() -> new IllegalStateException(
                        "Missing registered Cold Sweat TempModifier: "
                                + ColdSweatFabric.id(path)
                ));
    }

    private static void tickAll()
    {
        if (WORLD_MODIFIERS.isEmpty())
        {
            return;
        }

        for (LivingEntity entity : List.copyOf(WORLD_MODIFIERS.keySet()))
        {
            if (entity.isRemoved()
                    || !EntityTempManager.isTemperatureEnabled(entity))
            {
                removeEntity(entity);
                continue;
            }

            updateWaterExposure(entity);
            updateFreezingExposure(entity);
            tickWorldTemperature(entity);
        }
    }

    /**
     * Water/rain exposure remains dynamic and now belongs explicitly to the
     * exposure stage rather than being an untyped tail entry in WORLD.
     */
    private static void updateWaterExposure(LivingEntity entity)
    {
        if (!(entity instanceof Player player)
                || player.isSpectator()
                || player.tickCount % 5 != 0)
        {
            return;
        }

        boolean wet =
                player.isInWater()
                        || (player.tickCount % 40 == 0
                            && player.level().isRainingAt(
                                    player.blockPosition()
                            ));

        if (!wet)
        {
            return;
        }

        WorldModifierStages stages =
                WORLD_MODIFIERS.get(entity);

        if (stages == null
                || stages.exposure.stream()
                        .anyMatch(WaterTempModifier.class::isInstance))
        {
            return;
        }

        TempModifier water =
                createRegistered("water").tickRate(5);

        addWorldModifier(
                entity,
                stages.exposure,
                water
        );
    }

    /**
     * Vanilla freezing remains a BASE modifier. It is not folded into the
     * environment split because BASE represents direct body-state pressure.
     */
    private static void updateFreezingExposure(LivingEntity entity)
    {
        if (!(entity instanceof Player player)
                || player.isSpectator()
                || player.tickCount % 5 != 0
                || !player.isFreezing())
        {
            return;
        }

        List<TempModifier> modifiers =
                BASE_MODIFIERS.get(entity);

        if (modifiers == null)
        {
            return;
        }

        for (int i = 0; i < modifiers.size(); i++)
        {
            TempModifier modifier = modifiers.get(i);

            if (modifier instanceof FreezingTempModifier)
            {
                modifier.onRemoved(
                        entity,
                        Temperature.Trait.BASE
                );

                TempModifier replacement =
                        createRegistered("freezing");

                replacement.onAdded(
                        entity,
                        Temperature.Trait.BASE
                );

                modifiers.set(i, replacement);
                return;
            }
        }

        TempModifier freezing =
                createRegistered("freezing");

        freezing.onAdded(
                entity,
                Temperature.Trait.BASE
        );

        modifiers.add(freezing);
    }

    private static void tickWorldTemperature(LivingEntity entity)
    {
        WorldModifierStages stages = WORLD_MODIFIERS.get(entity);
        if (stages == null)
        {
            return;
        }

        /*
         * Evaluate each modifier exactly once through the existing cached
         * TempModifier machinery. This gives us semantic stage boundaries
         * without duplicating biome/cave/block/entity scans.
         */
        double rawAmbientClimate = Temperature.apply(
                0.0,
                entity,
                Temperature.Trait.WORLD,
                stages.ambient
        );

        EnvironmentSnapshot previous =
                ENVIRONMENT_SNAPSHOTS.get(entity);

        double ambientClimate =
                previous == null
                        ? rawAmbientClimate
                        : approach(
                                previous.ambientClimate(),
                                rawAmbientClimate,
                                AMBIENT_RESPONSE_PER_TICK
                        );

        double rawAfterLocalSources = Temperature.apply(
                ambientClimate,
                entity,
                Temperature.Trait.WORLD,
                stages.localSources
        );

        /*
         * Keep local/radiant load distinct from ambient climate and give that
         * load a small amount of thermal response time. This prevents a newly
         * exposed lava/fire source from making the player-facing environment
         * jump instantly while remaining responsive on survival timescales.
         *
         * This is O(1) and reuses the previous environment snapshot - no extra
         * source scan is introduced.
         */
        double rawLocalSourceDelta =
                rawAfterLocalSources - ambientClimate;

        double localSourceDelta =
                previous == null
                        ? rawLocalSourceDelta
                        : approach(
                                previous.localThermalLoad(),
                                rawLocalSourceDelta,
                                LOCAL_SOURCE_RESPONSE_PER_TICK
                        );

        double afterLocalSources =
                ambientClimate + localSourceDelta;

        double modifiedEffectiveTemperature = Temperature.apply(
                afterLocalSources,
                entity,
                Temperature.Trait.WORLD,
                stages.exposure
        );

        double worldTemperature =
                EntityTempManager.resolveAttributeValue(
                        entity,
                        Temperature.Trait.WORLD,
                        modifiedEffectiveTemperature
                );

        EnvironmentSnapshot.SpatialState spatial =
                previous != null
                        ? previous.spatial()
                        : EnvironmentSnapshot.SpatialState.unavailable();

        if (!spatial.available()
                || entity.tickCount % SPATIAL_SCAN_INTERVAL_TICKS == 0)
        {
            spatial =
                    EnvironmentSnapshotScanner.scan(entity);
        }

        ENVIRONMENT_SNAPSHOTS.put(
                entity,
                EnvironmentSnapshot.fromCurrentPipeline(
                        entity,
                        ambientClimate,
                        afterLocalSources,
                        worldTemperature,
                        spatial
                )
        );

        tickCoreTemperature(
                entity,
                worldTemperature
        );

        tickModifierLifecycle(
                entity,
                Temperature.Trait.WORLD,
                stages.ambient
        );
        tickModifierLifecycle(
                entity,
                Temperature.Trait.WORLD,
                stages.localSources
        );
        tickModifierLifecycle(
                entity,
                Temperature.Trait.WORLD,
                stages.exposure
        );

        List<TempModifier> baseModifiers =
                BASE_MODIFIERS.get(entity);

        if (baseModifiers != null)
        {
            tickModifierLifecycle(
                    entity,
                    Temperature.Trait.BASE,
                    baseModifiers
            );
        }
    }

    /**
     * Live player body-temperature runtime.
     *
     * M7.12b only changes environment ownership. The existing CORE behavior is
     * deliberately preserved for this slice so the high-inertia homeostatic
     * rewrite can be validated independently in a later slice.
     */
    private static void tickCoreTemperature(
            LivingEntity entity,
            double worldTemperature
    )
    {
        double storedCore =
                Temperature.get(
                        entity,
                        Temperature.Trait.CORE
                );

        List<TempModifier> baseModifiers =
                BASE_MODIFIERS.getOrDefault(
                        entity,
                        List.of()
                );

        double modifiedBaseTemperature =
                Temperature.apply(
                        0.0,
                        entity,
                        Temperature.Trait.BASE,
                        baseModifiers
                );

        double baseTemperature =
                EntityTempManager.resolveAttributeValue(
                        entity,
                        Temperature.Trait.BASE,
                        modifiedBaseTemperature
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

        double coldResistance =
                EntityTempManager.resolveAttributeValue(
                        entity,
                        Temperature.Trait.COLD_RESISTANCE,
                        0.0
                );

        double heatResistance =
                EntityTempManager.resolveAttributeValue(
                        entity,
                        Temperature.Trait.HEAT_RESISTANCE,
                        0.0
                );

        int worldTemperatureSign =
                TemperatureRuntime.getWorldTemperatureSign(
                        worldTemperature,
                        freezingPoint,
                        burningPoint
                );

        boolean creative =
                entity instanceof Player player
                        && player.isCreative();

        boolean peaceful =
                entity.level().getDifficulty()
                        == Difficulty.PEACEFUL;

        boolean immuneToPressure =
                creative
                        || entity.isSpectator()
                        || peaceful;

        double coreTemperature = storedCore;
        double rate = 0.0;

        if (worldTemperatureSign != 0
                && !immuneToPressure)
        {
            double rawRate =
                    TemperatureRuntime.calculateTemperatureRate(
                            worldTemperature,
                            freezingPoint,
                            burningPoint,
                            TemperatureRuntime.DEFAULT_TEMP_RATE,
                            coldDampening,
                            heatDampening
                    );

            rate =
                    EntityTempManager.resolveAttributeValue(
                            entity,
                            Temperature.Trait.RATE,
                            rawRate
                    );

            coreTemperature += rate;
        }

        double equilibrium =
                TemperatureRuntime.calculateEquilibriumDelta(
                        coreTemperature,
                        storedCore,
                        worldTemperature,
                        freezingPoint,
                        burningPoint,
                        TemperatureRuntime.DEFAULT_TEMP_RATE,
                        coldDampening,
                        heatDampening,
                        peaceful
                );

        int coreDeltaSign =
                TemperatureRuntime.sign(
                        coreTemperature - storedCore
                );

        int equilibriumSign =
                TemperatureRuntime.sign(equilibrium);

        /*
         * Match upstream: equilibrium must not fight a CORE modifier/rate that
         * is currently driving temperature in the opposite direction.
         */
        if (coreDeltaSign == 0
                || coreDeltaSign == equilibriumSign)
        {
            coreTemperature += equilibrium;
        }

        /*
         * PlayerTempCap parity: creative and spectator players do not merely
         * stop gaining heat/cold pressure; their CORE trait is forced to exact
         * neutral every tick.
         */
        if (creative || entity.isSpectator())
        {
            coreTemperature = 0.0;
            rate = 0.0;
        }

        EnumMap<Temperature.Trait, Double> values =
                new EnumMap<>(Temperature.Trait.class);

        values.put(
                Temperature.Trait.CORE,
                TemperatureRuntime.clamp(
                        coreTemperature,
                        -150.0,
                        150.0
                )
        );
        values.put(
                Temperature.Trait.BASE,
                TemperatureRuntime.clamp(
                        baseTemperature,
                        -150.0,
                        150.0
                )
        );
        values.put(
                Temperature.Trait.WORLD,
                worldTemperature
        );
        values.put(
                Temperature.Trait.BURNING_POINT,
                burningPoint
        );
        values.put(
                Temperature.Trait.FREEZING_POINT,
                freezingPoint
        );
        values.put(
                Temperature.Trait.COLD_RESISTANCE,
                coldResistance
        );
        values.put(
                Temperature.Trait.HEAT_RESISTANCE,
                heatResistance
        );
        values.put(
                Temperature.Trait.COLD_DAMPENING,
                coldDampening
        );
        values.put(
                Temperature.Trait.HEAT_DAMPENING,
                heatDampening
        );
        values.put(
                Temperature.Trait.RATE,
                rate
        );

        Temperature.setAll(entity, values);

        /*
         * Critical damage moved to TemperatureDamageRuntime in M7.11.
         * Keep this runtime focused on environment/core/effect state.
         */
        TemperatureEffectRuntime.applyServerEffects(entity);
    }

    private static double approach(
            double current,
            double target,
            double response
    )
    {
        if (Double.compare(current, target) == 0)
        {
            return target;
        }

        double clampedResponse =
                Math.max(
                        0.0,
                        Math.min(1.0, response)
                );

        double next =
                current + (target - current) * clampedResponse;

        return Math.abs(target - next) < 1.0e-9
                ? target
                : next;
    }

    private static void tickModifierLifecycle(
            LivingEntity entity,
            Temperature.Trait trait,
            List<TempModifier> modifiers
    )
    {
        for (int i = 0; i < modifiers.size(); i++)
        {
            TempModifier modifier = modifiers.get(i);

            if (modifier.getTicksExisted() % modifier.getTickRate() == 0)
            {
                modifier.tick(entity);
            }

            modifier.setTicksExisted(
                    modifier.getTicksExisted() + 1
            );

            int expireTime = modifier.getExpireTime();
            if (expireTime != -1
                    && modifier.getTicksExisted() > expireTime)
            {
                modifier.onRemoved(
                        entity,
                        trait
                );
                modifiers.remove(i);
                i--;
            }
        }
    }

    private static void removeEntity(LivingEntity entity)
    {
        TemperatureEffectRuntime.clear(entity);
        ENVIRONMENT_SNAPSHOTS.remove(entity);

        WorldModifierStages worldRemoved =
                WORLD_MODIFIERS.remove(entity);

        if (worldRemoved != null)
        {
            removeWorldStage(
                    entity,
                    worldRemoved.ambient
            );
            removeWorldStage(
                    entity,
                    worldRemoved.localSources
            );
            removeWorldStage(
                    entity,
                    worldRemoved.exposure
            );
        }

        List<TempModifier> baseRemoved =
                BASE_MODIFIERS.remove(entity);

        if (baseRemoved != null)
        {
            for (TempModifier modifier : baseRemoved)
            {
                modifier.onRemoved(
                        entity,
                        Temperature.Trait.BASE
                );
            }
        }
    }

    private static void removeWorldStage(
            LivingEntity entity,
            List<TempModifier> modifiers
    )
    {
        for (TempModifier modifier : modifiers)
        {
            modifier.onRemoved(
                    entity,
                    Temperature.Trait.WORLD
            );
        }
    }

    /**
     * Stable per-entity modifier ownership. Stage lists are allocated only when
     * the entity is installed, not during normal temperature ticks.
     */
    private static final class WorldModifierStages
    {
        private final List<TempModifier> ambient =
                new ArrayList<>();

        private final List<TempModifier> localSources =
                new ArrayList<>();

        private final List<TempModifier> exposure =
                new ArrayList<>();
    }

    private TemperatureModifierRuntime()
    {
    }
}
