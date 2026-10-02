package com.momosoftworks.coldsweat.common.capability.handler;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureData;
import com.momosoftworks.coldsweat.core.init.ModDataAttachments;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Fabric-side ownership boundary for Cold Sweat temperature state.
 *
 * The original NeoForge class also owns a large number of events, modifier
 * calculations, equipment hooks, and compatibility paths. Those are restored
 * incrementally. This foundation centralizes which entities have temperature
 * state and all direct access to the Fabric Data Attachment.
 */
public final class EntityTempManager
{
    public static final Temperature.Trait[] VALID_TEMPERATURE_TRAITS =
            Arrays.stream(Temperature.Trait.values())
                    .filter(Temperature.Trait::isForTemperature)
                    .toArray(Temperature.Trait[]::new);

    public static final Temperature.Trait[] VALID_MODIFIER_TRAITS =
            Arrays.stream(Temperature.Trait.values())
                    .filter(Temperature.Trait::isForModifiers)
                    .toArray(Temperature.Trait[]::new);

    public static final Temperature.Trait[] VALID_ATTRIBUTE_TRAITS =
            Arrays.stream(Temperature.Trait.values())
                    .filter(Temperature.Trait::isForAttributes)
                    .toArray(Temperature.Trait[]::new);

    private static final Identifier PLAYER_TYPE_ID = Identifier.withDefaultNamespace("player");

    private static final Set<EntityType<? extends LivingEntity>> TEMPERATURE_ENABLED_ENTITIES =
            new LinkedHashSet<>();

    public static Set<EntityType<? extends LivingEntity>> getEntitiesWithTemperature()
    {
        return Set.copyOf(TEMPERATURE_ENABLED_ENTITIES);
    }

    public static boolean isTemperatureEnabled(EntityType<?> type)
    {
        return TEMPERATURE_ENABLED_ENTITIES.contains(type);
    }

    public static boolean isTemperatureEnabled(Entity entity)
    {
        return entity instanceof LivingEntity && isTemperatureEnabled(entity.getType());
    }

    /**
     * Allows later config/content initialization to opt additional living
     * entity types into Cold Sweat temperature state.
     */
    public static void enableTemperatureFor(EntityType<? extends LivingEntity> type)
    {
        TEMPERATURE_ENABLED_ENTITIES.add(type);
    }

    /**
     * Returns the entity's Fabric-backed temperature data, creating the
     * attachment's default immutable value when required.
     */
    public static Optional<TemperatureData> getTemperatureData(Entity entity)
    {
        if (!(entity instanceof LivingEntity living) || !isTemperatureEnabled(living))
        {
            return Optional.empty();
        }

        return Optional.of(living.getAttachedOrCreate(ModDataAttachments.ENTITY_TEMPERATURE));
    }

    /**
     * Replaces the complete immutable temperature state for an enabled entity.
     */
    public static boolean setTemperatureData(LivingEntity entity, TemperatureData data)
    {
        if (!isTemperatureEnabled(entity))
        {
            return false;
        }

        entity.setAttached(ModDataAttachments.ENTITY_TEMPERATURE, data);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void registerPlayerType()
    {
        /*
         * Minecraft 26.2's published mappings no longer expose EntityType.PLAYER
         * in the compile surface used by this Fabric workspace. Resolve the
         * canonical minecraft:player type from the built-in registry instead.
         */
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE)
        {
            if (PLAYER_TYPE_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type)))
            {
                TEMPERATURE_ENABLED_ENTITIES.add((EntityType<? extends LivingEntity>) type);
                return;
            }
        }

        throw new IllegalStateException("Could not resolve minecraft:player entity type");
    }

    public static void initialize()
    {
        registerPlayerType();

        ColdSweatFabric.LOGGER.info(
                "Initializing Cold Sweat entity temperature manager with {} enabled entity type(s).",
                TEMPERATURE_ENABLED_ENTITIES.size()
        );
    }

    private EntityTempManager()
    {
    }
}
