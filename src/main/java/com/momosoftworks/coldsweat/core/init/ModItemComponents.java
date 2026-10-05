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

    /*
     * M8.5 reusable-flask state.
     *
     * Capacity is intrinsic to the FlaskItem tier and is intentionally not a
     * mutable component. These three values are the state that must survive
     * smithing upgrades and ordinary ItemStack serialization.
     */
    public static final DataComponentType<Integer> FLASK_WATER_AMOUNT = register(
            "flask_water_amount",
            DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 60))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build()
    );

    public static final DataComponentType<Boolean> FLASK_PURIFIED = register(
            "flask_purified",
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build()
    );

    public static final DataComponentType<Integer> FLASK_FILTER_CHARGES = register(
            "flask_filter_charges",
            DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 5))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
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
