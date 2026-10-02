package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public final class ModAttributes
{
    public static final Holder<Attribute> WORLD_TEMPERATURE = register(
            "world_temperature",
            new RangedAttribute("attribute.world_temperature", Double.NaN, Double.NaN, Double.POSITIVE_INFINITY).setSyncable(true)
    );

    public static final Holder<Attribute> BASE_BODY_TEMPERATURE = register(
            "base_temperature",
            new RangedAttribute("attribute.base_temperature", Double.NaN, Double.NaN, Double.POSITIVE_INFINITY).setSyncable(true)
    );

    public static final Holder<Attribute> TEMP_RATE = register(
            "temperature_rate",
            new RangedAttribute("attribute.temperature_rate", Double.NaN, Double.NaN, Double.POSITIVE_INFINITY).setSyncable(true)
    );

    public static final Holder<Attribute> BURNING_POINT = register(
            "burning_point",
            new RangedAttribute("attribute.burning_point", Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).setSyncable(true)
    );

    public static final Holder<Attribute> FREEZING_POINT = register(
            "freezing_point",
            new RangedAttribute("attribute.freezing_point", Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).setSyncable(true)
    );

    public static final Holder<Attribute> HEAT_RESISTANCE = register(
            "heat_resistance",
            new RangedAttribute("attribute.heat_resistance", 0.0, 0.0, 1.0).setSyncable(true)
    );

    public static final Holder<Attribute> COLD_RESISTANCE = register(
            "cold_resistance",
            new RangedAttribute("attribute.cold_resistance", 0.0, 0.0, 1.0).setSyncable(true)
    );

    public static final Holder<Attribute> HEAT_DAMPENING = register(
            "heat_dampening",
            new RangedAttribute("attribute.heat_dampening", 0.0, Double.NEGATIVE_INFINITY, 1.0).setSyncable(true)
    );

    public static final Holder<Attribute> COLD_DAMPENING = register(
            "cold_dampening",
            new RangedAttribute("attribute.cold_dampening", 0.0, Double.NEGATIVE_INFINITY, 1.0).setSyncable(true)
    );

    private static Holder<Attribute> register(String path, Attribute attribute)
    {
        return Registry.registerForHolder(
                BuiltInRegistries.ATTRIBUTE,
                ColdSweatFabric.id(path),
                attribute
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat attributes.");
    }

    private ModAttributes()
    {
    }
}
