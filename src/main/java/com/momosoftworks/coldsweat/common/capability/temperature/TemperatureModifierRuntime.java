package com.momosoftworks.coldsweat.common.capability.temperature;

import com.momosoftworks.coldsweat.api.registry.TempModifierRegistry;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.WaterTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
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

/**
 * Server-side runtime owner for environmental TempModifiers.
 */
public final class TemperatureModifierRuntime
{
    private static final Map<LivingEntity, List<TempModifier>> WORLD_MODIFIERS =
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
     * Current standalone player WORLD chain, preserving upstream ordering and
     * player tick rates for the modifiers already ported:
     *
     * Biome -> Shade -> Elevation -> Cave Biomes -> Blocks
     */
    public static void installDefaultWorldModifiers(LivingEntity entity)
    {
        removeEntity(entity);

        List<TempModifier> modifiers = new ArrayList<>();

        modifiers.add(
                createRegistered("biomes").tickRate(20)
        );
        modifiers.add(
                createRegistered("shade").tickRate(10)
        );
        modifiers.add(
                createRegistered("elevation").tickRate(20)
        );
        modifiers.add(
                createRegistered("cave_biomes").tickRate(20)
        );
        modifiers.add(
                createRegistered("blocks").tickRate(5)
        );

        for (TempModifier modifier : modifiers)
        {
            modifier.onAdded(entity, Temperature.Trait.WORLD);
        }

        WORLD_MODIFIERS.put(entity, modifiers);
    }

    public static List<TempModifier> getWorldModifiers(LivingEntity entity)
    {
        return WORLD_MODIFIERS.getOrDefault(entity, List.of());
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
            tickWorldTemperature(entity);
        }
    }

    /**
     * Upstream adds WaterTempModifier dynamically for players rather than as a
     * permanent default modifier:
     * - water is checked every 5 ticks
     * - rain is sampled every 40 ticks
     * - duplicate WaterTempModifiers are not added
     * - the modifier later expires naturally after drying back to zero
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

        List<TempModifier> modifiers =
                WORLD_MODIFIERS.get(entity);

        if (modifiers == null
                || modifiers.stream()
                        .anyMatch(WaterTempModifier.class::isInstance))
        {
            return;
        }

        TempModifier water =
                createRegistered("water").tickRate(5);

        water.onAdded(
                entity,
                Temperature.Trait.WORLD
        );
        modifiers.add(water);
    }

    private static void tickWorldTemperature(LivingEntity entity)
    {
        List<TempModifier> modifiers = WORLD_MODIFIERS.get(entity);
        if (modifiers == null)
        {
            return;
        }

        double modifiedWorldTemperature = Temperature.apply(
                0.0,
                entity,
                Temperature.Trait.WORLD,
                modifiers
        );

        double worldTemperature =
                EntityTempManager.resolveAttributeValue(
                        entity,
                        Temperature.Trait.WORLD,
                        modifiedWorldTemperature
                );

        tickCoreTemperature(
                entity,
                worldTemperature
        );

        tickModifierLifecycle(entity, modifiers);
    }

    /**
     * Live player body-temperature runtime.
     *
     * Environmental WORLD temperature is now meaningful enough to drive CORE.
     * Damage/effects are deliberately not applied here yet; this slice only
     * restores the underlying trait evolution and keeps it synchronized.
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

        double baseTemperature =
                EntityTempManager.resolveAttributeValue(
                        entity,
                        Temperature.Trait.BASE,
                        0.0
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
    }

    private static void tickModifierLifecycle(
            LivingEntity entity,
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
                        Temperature.Trait.WORLD
                );
                modifiers.remove(i);
                i--;
            }
        }
    }

    private static void removeEntity(LivingEntity entity)
    {
        List<TempModifier> removed = WORLD_MODIFIERS.remove(entity);
        if (removed == null)
        {
            return;
        }

        for (TempModifier modifier : removed)
        {
            modifier.onRemoved(entity, Temperature.Trait.WORLD);
        }
    }

    private TemperatureModifierRuntime()
    {
    }
}
