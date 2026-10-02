package com.momosoftworks.coldsweat.api.util;

import com.mojang.serialization.Codec;
import com.momosoftworks.coldsweat.api.event.common.temperautre.TemperatureChangedEvent;
import com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumMap;

/**
 * General helper class for temperature-related actions.
 *
 * M3 is restoring this class incrementally. Unit/trait behavior and persistent
 * synchronized entity trait access are live; modifiers are restored in later
 * slices.
 */
public final class Temperature
{
    private Temperature()
    {
    }

    public static double convert(double value, Units from, Units to, boolean absolute)
    {
        return switch (from)
        {
            case C -> switch (to)
            {
                case C -> value;
                case F -> value * 1.8 + (absolute ? 32d : 0d);
                case MC -> value / 25d;
            };
            case F -> switch (to)
            {
                case C -> (value - (absolute ? 32d : 0d)) / 1.8;
                case F -> value;
                case MC -> (value - (absolute ? 32d : 0d)) / 45d;
            };
            case MC -> switch (to)
            {
                case C -> value * 25d;
                case F -> value * 45d + (absolute ? 32d : 0d);
                case MC -> value;
            };
        };
    }

    public static double convertIfNeeded(double value, Trait trait, Units units, boolean absolute)
    {
        if (trait.isForWorld())
        {
            return convert(value, Units.MC, units, absolute);
        }
        return value;
    }

    public static double get(LivingEntity entity, Trait trait)
    {
        return EntityTempManager.getTemperatureData(entity)
                .map(data -> data.getTrait(trait))
                .orElse(0.0);
    }

    public static void set(LivingEntity entity, Trait trait, double value)
    {
        double oldValue = get(entity, trait);
        TemperatureChangedEvent event =
                TemperatureChangedEvent.fire(entity, trait, oldValue, value);

        if (event.isCanceled())
        {
            return;
        }

        double newValue = event.getTemperature();
        if (Double.compare(oldValue, newValue) == 0)
        {
            return;
        }

        EntityTempManager.getTemperatureData(entity).ifPresent(data ->
        {
            TemperatureData updated = data.withTrait(trait, newValue);
            if (updated != data)
            {
                EntityTempManager.setTemperatureData(entity, updated);
            }
        });
    }

    public static void add(LivingEntity entity, Trait trait, double value)
    {
        double oldValue = get(entity, trait);
        TemperatureChangedEvent event =
                TemperatureChangedEvent.fire(entity, trait, oldValue, oldValue + value);

        if (event.isCanceled())
        {
            return;
        }

        double newValue = event.getTemperature();
        if (Double.compare(oldValue, newValue) == 0)
        {
            return;
        }

        EntityTempManager.getTemperatureData(entity).ifPresent(data ->
        {
            TemperatureData updated = data.withTrait(trait, newValue);
            if (updated != data)
            {
                EntityTempManager.setTemperatureData(entity, updated);
            }
        });
    }

    public static EnumMap<Trait, Double> getTemperatures(LivingEntity entity)
    {
        return EntityTempManager.getTemperatureData(entity)
                .map(TemperatureData::copyTraits)
                .orElseGet(() -> new EnumMap<>(Trait.class));
    }

    private static <T extends Enum<T> & StringRepresentable> Codec<T> enumIgnoreCase(T[] values)
    {
        return Codec.STRING.xmap(
                name -> {
                    if (values.length == 0)
                    {
                        throw new IllegalArgumentException("Enum has no values");
                    }

                    for (T value : values)
                    {
                        if (value.getSerializedName().equalsIgnoreCase(name))
                        {
                            return value;
                        }
                    }

                    throw new IllegalArgumentException(
                            "Unknown " + values[0].getClass().getSimpleName() + " value: " + name
                    );
                },
                StringRepresentable::getSerializedName
        );
    }

    public enum Trait implements StringRepresentable
    {
        WORLD("world", true, true, true),
        CORE("core", true, true, false),
        BASE("base", true, true, true),
        BODY("body", false, false, false),
        RATE("rate", true, true, true),

        FREEZING_POINT("freezing_point", true, true, true),
        BURNING_POINT("burning_point", true, true, true),
        COLD_RESISTANCE("cold_resistance", true, true, true),
        HEAT_RESISTANCE("heat_resistance", true, true, true),
        COLD_DAMPENING("cold_dampening", true, true, true),
        HEAT_DAMPENING("heat_dampening", true, true, true);

        public static final Codec<Trait> CODEC = enumIgnoreCase(values());

        public static final StreamCodec<ByteBuf, Trait> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(
                        index -> {
                            Trait[] values = values();
                            if (index < 0 || index >= values.length)
                            {
                                throw new IllegalArgumentException("Invalid temperature trait network id: " + index);
                            }
                            return values[index];
                        },
                        Trait::ordinal
                );

        private final String id;
        private final boolean forTemperature;
        private final boolean forModifiers;
        private final boolean forAttributes;

        Trait(String id, boolean forTemperature, boolean forModifiers, boolean forAttributes)
        {
            this.id = id;
            this.forTemperature = forTemperature;
            this.forModifiers = forModifiers;
            this.forAttributes = forAttributes;
        }

        public boolean isForTemperature()
        {
            return forTemperature;
        }

        public boolean isForModifiers()
        {
            return forModifiers;
        }

        public boolean isForAttributes()
        {
            return forAttributes;
        }

        public boolean isForWorld()
        {
            return this == WORLD || this == BURNING_POINT || this == FREEZING_POINT;
        }

        public boolean isProportional()
        {
            return this == COLD_RESISTANCE
                    || this == HEAT_RESISTANCE
                    || this == COLD_DAMPENING
                    || this == HEAT_DAMPENING
                    || this == RATE;
        }

        public boolean isNegativeValueGood()
        {
            return this == FREEZING_POINT;
        }

        public static Trait fromID(String name)
        {
            for (Trait trait : values())
            {
                if (trait.id.equalsIgnoreCase(name) || trait.name().equalsIgnoreCase(name))
                {
                    return trait;
                }
            }
            throw new IllegalArgumentException("Unknown temperature trait: " + name);
        }

        @Override
        public String getSerializedName()
        {
            return id;
        }

        public String getFormattedName()
        {
            return Component.translatable("trait.cold_sweat." + id).getString();
        }
    }

    public enum Units implements StringRepresentable
    {
        F(
                "f",
                Component.translatable("cold_sweat.units.fahrenheit.value"),
                Component.translatable("cold_sweat.units.fahrenheit.name")
        ),
        C(
                "c",
                Component.translatable("cold_sweat.units.celsius.value"),
                Component.translatable("cold_sweat.units.celsius.name")
        ),
        MC(
                "mc",
                Component.translatable("cold_sweat.units.minecraft.value"),
                Component.translatable("cold_sweat.units.minecraft.name")
        );

        public static final Codec<Units> CODEC = enumIgnoreCase(values());

        private final String id;
        private final Component name;
        private final Component fullName;

        Units(String id, Component name, Component fullName)
        {
            this.id = id;
            this.name = name;
            this.fullName = fullName;
        }

        public static Units fromID(String name)
        {
            for (Units unit : values())
            {
                if (unit.id.equalsIgnoreCase(name) || unit.name().equalsIgnoreCase(name))
                {
                    return unit;
                }
            }
            throw new IllegalArgumentException("Unknown temperature unit: " + name);
        }

        public Component getFormattedName()
        {
            return name;
        }

        public Component getFullName()
        {
            return fullName;
        }

        @Override
        public String getSerializedName()
        {
            return id;
        }
    }
}
