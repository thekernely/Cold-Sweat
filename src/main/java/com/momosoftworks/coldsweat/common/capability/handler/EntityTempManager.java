package com.momosoftworks.coldsweat.common.capability.handler;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.insulation.ArmorInsulationRuntime;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureData;
import com.momosoftworks.coldsweat.core.init.ModAttributes;
import com.momosoftworks.coldsweat.core.init.ModDataAttachments;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Fabric-side ownership boundary for Cold Sweat temperature state.
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

    private static final Identifier PLAYER_TYPE_ID =
            Identifier.withDefaultNamespace("player");

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

    public static Holder<Attribute> getAttributeHolder(Temperature.Trait trait)
    {
        return switch (trait)
        {
            case WORLD           -> ModAttributes.WORLD_TEMPERATURE;
            case BASE            -> ModAttributes.BASE_BODY_TEMPERATURE;
            case RATE            -> ModAttributes.TEMP_RATE;
            case FREEZING_POINT  -> ModAttributes.FREEZING_POINT;
            case BURNING_POINT   -> ModAttributes.BURNING_POINT;
            case HEAT_RESISTANCE -> ModAttributes.HEAT_RESISTANCE;
            case COLD_RESISTANCE -> ModAttributes.COLD_RESISTANCE;
            case HEAT_DAMPENING  -> ModAttributes.HEAT_DAMPENING;
            case COLD_DAMPENING  -> ModAttributes.COLD_DAMPENING;
            default              -> null;
        };
    }

    public static Temperature.Trait getTraitForAttribute(Holder<Attribute> attribute)
    {
        if (attribute == null)
        {
            return null;
        }

        for (Temperature.Trait trait : VALID_ATTRIBUTE_TRAITS)
        {
            Holder<Attribute> candidate = getAttributeHolder(trait);
            if (attribute.equals(candidate))
            {
                return trait;
            }
        }

        return null;
    }

    public static AttributeInstance getAttribute(Temperature.Trait trait, LivingEntity entity)
    {
        Holder<Attribute> attribute = getAttributeHolder(trait);
        return attribute != null ? entity.getAttribute(attribute) : null;
    }

    public static List<AttributeInstance> getAllTemperatureAttributes(LivingEntity entity)
    {
        return Arrays.stream(VALID_ATTRIBUTE_TRAITS)
                .map(trait -> getAttribute(trait, entity))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Resolves a temperature attribute with Cold Sweat's NaN-fallback semantics.
     * Armor insulation is a RATE TempModifier upstream, so its Fabric runtime is
     * applied after the attribute operations have produced the raw RATE value.
     */
    public static double resolveAttributeValue(
            LivingEntity entity,
            Temperature.Trait trait,
            double fallbackValue
    )
    {
        AttributeInstance attribute = getAttribute(trait, entity);
        if (attribute == null)
        {
            return trait == Temperature.Trait.RATE
                    ? ArmorInsulationRuntime.applyRate(entity, fallbackValue)
                    : fallbackValue;
        }

        double base = attribute.getBaseValue();
        if (Double.isNaN(base))
        {
            base = fallbackValue;
        }

        for (AttributeModifier modifier : attribute.getModifiers())
        {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE)
            {
                base += modifier.amount();
            }
        }

        double value = base;

        for (AttributeModifier modifier : attribute.getModifiers())
        {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
            {
                value += base * modifier.amount();
            }
        }

        for (AttributeModifier modifier : attribute.getModifiers())
        {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            {
                value *= 1.0 + modifier.amount();
            }
        }

        if (trait == Temperature.Trait.RATE)
        {
            value = ArmorInsulationRuntime.applyRate(entity, value);
        }

        return value;
    }

    private static void registerEntityLoadLifecycle()
    {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) ->
        {
            if (entity instanceof LivingEntity living && isTemperatureEnabled(living))
            {
                living.getAttachedOrCreate(ModDataAttachments.ENTITY_TEMPERATURE);
            }
        });
    }

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
        registerEntityLoadLifecycle();
        registerPlayerLifecycle();

        ColdSweatFabric.LOGGER.info(
                "Initializing Cold Sweat entity temperature manager with {} enabled entity type(s), entity-load initialization, and player lifecycle hooks.",
                TEMPERATURE_ENABLED_ENTITIES.size()
        );
    }

    private EntityTempManager()
    {
    }
}
