package com.momosoftworks.coldsweat.core.init;

import com.mojang.serialization.Codec;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModItemComponents
{
    /*
     * These scalar components are loader-independent and can be ported now.
     *
     * SOULSPRING_LAMP_DATA and ARMOR_INSULATION are intentionally deferred
     * until their custom payload/capability classes are migrated in later
     * gameplay milestones.
     */

    public static final DataComponentType<Double> ARMOR_ADAPTATION = register(
            "armor_adaptation",
            DataComponentType.<Double>builder()
                    .persistent(Codec.DOUBLE)
                    .networkSynchronized(ByteBufCodecs.DOUBLE)
                    .build()
    );

    public static final DataComponentType<Double> WATER_TEMPERATURE = register(
            "temperature",
            DataComponentType.<Double>builder()
                    .persistent(Codec.DOUBLE)
                    .networkSynchronized(ByteBufCodecs.DOUBLE)
                    .build()
    );

    @Deprecated(since = "2.4", forRemoval = true)
    public static final DataComponentType<Double> SOULSPRING_LAMP_FUEL = register(
            "fuel",
            DataComponentType.<Double>builder()
                    .persistent(Codec.DOUBLE)
                    .networkSynchronized(ByteBufCodecs.DOUBLE)
                    .build()
    );

    private static <T> DataComponentType<T> register(String path, DataComponentType<T> type)
    {
        return Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                ColdSweatFabric.id(path),
                type
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat scalar item components.");
    }

    private ModItemComponents()
    {
    }
}
