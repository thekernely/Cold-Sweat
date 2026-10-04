package com.momosoftworks.coldsweat.common.capability.hydration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable persistent/synchronized hydration state.
 *
 * M8.1 deliberately owns only state. Depletion, drinking, contamination,
 * thermoregulation coupling, and HUD behavior are layered on top in later
 * slices.
 */
public final class HydrationData
{
    public static final double MAX_HYDRATION = 20.0;
    public static final double DEFAULT_HYDRATION = MAX_HYDRATION;

    /*
     * Saturation/exhaustion behavior is intentionally not balanced in M8.1.
     * Zero is a neutral foundation default until the depletion loop lands.
     */
    public static final double DEFAULT_SATURATION = 0.0;
    public static final double DEFAULT_EXHAUSTION = 0.0;

    public static final Codec<HydrationData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE
                            .optionalFieldOf(
                                    "hydration",
                                    DEFAULT_HYDRATION
                            )
                            .forGetter(HydrationData::hydration),
                    Codec.DOUBLE
                            .optionalFieldOf(
                                    "saturation",
                                    DEFAULT_SATURATION
                            )
                            .forGetter(HydrationData::saturation),
                    Codec.DOUBLE
                            .optionalFieldOf(
                                    "exhaustion",
                                    DEFAULT_EXHAUSTION
                            )
                            .forGetter(HydrationData::exhaustion)
            ).apply(instance, HydrationData::new));

    public static final StreamCodec<ByteBuf, HydrationData> STREAM_CODEC =
            new StreamCodec<>()
            {
                @Override
                public HydrationData decode(ByteBuf buffer)
                {
                    return new HydrationData(
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble()
                    );
                }

                @Override
                public void encode(
                        ByteBuf buffer,
                        HydrationData data
                )
                {
                    buffer.writeDouble(data.hydration());
                    buffer.writeDouble(data.saturation());
                    buffer.writeDouble(data.exhaustion());
                }
            };

    private final double hydration;
    private final double saturation;
    private final double exhaustion;

    public HydrationData()
    {
        this(
                DEFAULT_HYDRATION,
                DEFAULT_SATURATION,
                DEFAULT_EXHAUSTION
        );
    }

    public HydrationData(
            double hydration,
            double saturation,
            double exhaustion
    )
    {
        this.hydration =
                clamp(
                        finiteOrDefault(
                                hydration,
                                DEFAULT_HYDRATION
                        ),
                        0.0,
                        MAX_HYDRATION
                );

        /*
         * Hunger-style invariant: saturation cannot exceed the currently
         * available hydration points. This is structural, not a final M8
         * balance decision.
         */
        this.saturation =
                clamp(
                        finiteOrDefault(
                                saturation,
                                DEFAULT_SATURATION
                        ),
                        0.0,
                        this.hydration
                );

        this.exhaustion =
                Math.max(
                        0.0,
                        finiteOrDefault(
                                exhaustion,
                                DEFAULT_EXHAUSTION
                        )
                );
    }

    public double hydration()
    {
        return hydration;
    }

    public double saturation()
    {
        return saturation;
    }

    public double exhaustion()
    {
        return exhaustion;
    }

    public HydrationData withHydration(double value)
    {
        if (Double.compare(hydration, value) == 0)
        {
            return this;
        }

        return new HydrationData(
                value,
                saturation,
                exhaustion
        );
    }

    public HydrationData withSaturation(double value)
    {
        if (Double.compare(saturation, value) == 0)
        {
            return this;
        }

        return new HydrationData(
                hydration,
                value,
                exhaustion
        );
    }

    public HydrationData withExhaustion(double value)
    {
        if (Double.compare(exhaustion, value) == 0)
        {
            return this;
        }

        return new HydrationData(
                hydration,
                saturation,
                value
        );
    }

    private static double finiteOrDefault(
            double value,
            double fallback
    )
    {
        return Double.isFinite(value)
                ? value
                : fallback;
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum
    )
    {
        return Math.max(
                minimum,
                Math.min(maximum, value)
        );
    }
}
