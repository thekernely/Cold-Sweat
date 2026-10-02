package com.momosoftworks.coldsweat.common.capability.temperature;

import com.momosoftworks.coldsweat.api.temperature.modifier.BiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.ElevationTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.ShadeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side runtime owner for environmental TempModifiers.
 *
 * Upstream Cold Sweat rebuilds default modifiers whenever a temperature-enabled
 * entity joins the world, then ticks/calculates those modifiers every entity
 * tick. This Fabric boundary mirrors that behavior without yet pulling in the
 * larger placement/event/config stack.
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
     * Biome -> Shade -> Elevation
     *
     * Cave, block, nearby-entity, config, and compatibility modifiers join this
     * chain in later M4 slices.
     */
    public static void installDefaultWorldModifiers(LivingEntity entity)
    {
        removeEntity(entity);

        List<TempModifier> modifiers = new ArrayList<>();

        modifiers.add(new BiomeTempModifier(49).tickRate(20));
        modifiers.add(new ShadeTempModifier().tickRate(10));
        modifiers.add(new ElevationTempModifier(49).tickRate(20));

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

    private static void tickAll()
    {
        if (WORLD_MODIFIERS.isEmpty())
        {
            return;
        }

        for (LivingEntity entity : List.copyOf(WORLD_MODIFIERS.keySet()))
        {
            if (entity.isRemoved() || !EntityTempManager.isTemperatureEnabled(entity))
            {
                removeEntity(entity);
                continue;
            }

            tickWorldTemperature(entity);
        }
    }

    private static void tickWorldTemperature(LivingEntity entity)
    {
        List<TempModifier> modifiers = WORLD_MODIFIERS.get(entity);
        if (modifiers == null)
        {
            return;
        }

        /*
         * Upstream WORLD calculation begins at zero, runs the TempModifier
         * chain, then applies the WORLD attribute layer.
         */
        double modifiedWorldTemperature = Temperature.apply(
                0.0,
                entity,
                Temperature.Trait.WORLD,
                modifiers
        );

        double worldTemperature = EntityTempManager.resolveAttributeValue(
                entity,
                Temperature.Trait.WORLD,
                modifiedWorldTemperature
        );

        Temperature.set(
                entity,
                Temperature.Trait.WORLD,
                worldTemperature
        );

        tickModifierLifecycle(entity, modifiers);
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

            modifier.setTicksExisted(modifier.getTicksExisted() + 1);

            int expireTime = modifier.getExpireTime();
            if (expireTime != -1 && modifier.getTicksExisted() > expireTime)
            {
                modifier.onRemoved(entity, Temperature.Trait.WORLD);
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
