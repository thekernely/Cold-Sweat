package com.momosoftworks.coldsweat.common.capability.handler;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureData;
import com.momosoftworks.coldsweat.core.init.ModDataAttachments;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
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

    public static void enableTemperatureFor(EntityType<? extends LivingEntity> type)
    {
        TEMPERATURE_ENABLED_ENTITIES.add(type);
    }

    public static Optional<TemperatureData> getTemperatureData(Entity entity)
    {
        if (!(entity instanceof LivingEntity living) || !isTemperatureEnabled(living))
        {
            return Optional.empty();
        }

        return Optional.of(living.getAttachedOrCreate(ModDataAttachments.ENTITY_TEMPERATURE));
    }

    public static boolean setTemperatureData(LivingEntity entity, TemperatureData data)
    {
        if (!isTemperatureEnabled(entity))
        {
            return false;
        }

        entity.setAttached(ModDataAttachments.ENTITY_TEMPERATURE, data);
        return true;
    }

    /**
     * Preserve Cold Sweat temperature state when Minecraft replaces a live
     * ServerPlayer (for example, returning from the End).
     *
     * Fabric's COPY_FROM callback passes alive=false for death respawns, so
     * those intentionally receive fresh/default temperature data. This matches
     * upstream Cold Sweat's NeoForge Clone behavior, which only copies the
     * temperature capability when the clone was not caused by death.
     */
    private static void registerPlayerLifecycle()
    {
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
        {
            if (!alive)
            {
                return;
            }

            getTemperatureData(oldPlayer).ifPresent(data ->
                    setTemperatureData(newPlayer, data)
            );
        });
    }

    @SuppressWarnings("unchecked")
    private static void registerPlayerType()
    {
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
        registerPlayerLifecycle();

        ColdSweatFabric.LOGGER.info(
                "Initializing Cold Sweat entity temperature manager with {} enabled entity type(s) and player lifecycle hooks.",
                TEMPERATURE_ENABLED_ENTITIES.size()
        );
    }

    private EntityTempManager()
    {
    }
}
