package com.momosoftworks.coldsweat.core.init;

import com.mojang.serialization.Codec;
import com.momosoftworks.coldsweat.common.capability.insulation.ItemInsulationCap;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModItemComponents
{
    /*
     * Item-backed runtime state. Soulspring lamp structured data remains
     * deferred until the lamp itself is ported; sewn armor insulation is now
     * restored as part of M5.
     */

    public static final DataComponentType<ItemInsulationCap> ARMOR_INSULATION = register(
            "armor_insulation",
            DataComponentType.<ItemInsulationCap>builder()
                    .persistent(ItemInsulationCap.CODEC)
                    .networkSynchronized(ItemInsulationCap.STREAM_CODEC)
                    .build()
    );

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
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat item components.");
    }

    private ModItemComponents()
    {
    }
}
