package com.momosoftworks.coldsweat.common.capability.temperature;

import com.mojang.serialization.Codec;
import com.momosoftworks.coldsweat.api.util.Temperature;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable persistent and network-syncable temperature-trait state.
 */
public final class TemperatureData
{
    public static final Codec<TemperatureData> CODEC =
            Codec.unboundedMap(Temperature.Trait.CODEC, Codec.DOUBLE)
                    .xmap(TemperatureData::new, TemperatureData::traits);

    public static final StreamCodec<ByteBuf, TemperatureData> STREAM_CODEC =
            ByteBufCodecs.map(
                            size -> new EnumMap<>(Temperature.Trait.class),
                            Temperature.Trait.STREAM_CODEC,
                            ByteBufCodecs.DOUBLE
                    )
                    .map(
                            TemperatureData::new,
                            TemperatureData::copyTraits
                    );

    private final Map<Temperature.Trait, Double> traits;

    public TemperatureData()
    {
        this(Map.of());
    }

    public TemperatureData(Map<Temperature.Trait, Double> traits)
    {
        EnumMap<Temperature.Trait, Double> copy = new EnumMap<>(Temperature.Trait.class);
        copy.putAll(traits);
        copy.remove(Temperature.Trait.BODY);
        this.traits = Collections.unmodifiableMap(copy);
    }

    public double getTrait(Temperature.Trait trait)
    {
        if (trait == Temperature.Trait.BODY)
        {
            return getTrait(Temperature.Trait.CORE) + getTrait(Temperature.Trait.BASE);
        }

        validateStoredTrait(trait);
        return traits.getOrDefault(trait, 0.0);
    }

    public TemperatureData withTrait(Temperature.Trait trait, double value)
    {
        validateStoredTrait(trait);

        EnumMap<Temperature.Trait, Double> updated = new EnumMap<>(Temperature.Trait.class);
        updated.putAll(traits);
        updated.put(trait, value);
        return new TemperatureData(updated);
    }

    public Map<Temperature.Trait, Double> traits()
    {
        return traits;
    }

    public EnumMap<Temperature.Trait, Double> copyTraits()
    {
        EnumMap<Temperature.Trait, Double> copy = new EnumMap<>(Temperature.Trait.class);
        copy.putAll(traits);
        return copy;
    }

    private static void validateStoredTrait(Temperature.Trait trait)
    {
        if (!trait.isForTemperature())
        {
            throw new IllegalArgumentException("Invalid stored temperature trait: " + trait);
        }
    }
}
