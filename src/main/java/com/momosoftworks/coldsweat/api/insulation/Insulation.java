package com.momosoftworks.coldsweat.api.insulation;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Loader-independent insulation value model.
 *
 * <p>This is intentionally kept independent from the upstream configuration/
 * requirement graph so armor and item insulation can be restored in small
 * dependency-driven slices on Fabric.</p>
 */
public abstract class Insulation
{
    /**
     * Upstream uses a custom lenient number codec so integral JSON values are
     * accepted anywhere an insulation double is expected. Keep that behavior
     * here without pulling the much larger ExtraCodecs utility graph forward.
     */
    static final Codec<Double> DOUBLE_CODEC = new Codec<Number>()
    {
        @Override
        public <T> DataResult<Pair<Number, T>> decode(DynamicOps<T> ops, T input)
        {
            return ops.getNumberValue(input).map(number -> Pair.of(number, input));
        }

        @Override
        public <T> DataResult<T> encode(Number input, DynamicOps<T> ops, T prefix)
        {
            return ops.mergeToPrimitive(prefix, ops.createNumeric(input));
        }

        @Override
        public String toString()
        {
            return "ColdSweatLenientDouble";
        }
    }.xmap(Number::doubleValue, value -> value);

    private static Codec<Insulation> codec;

    public static Codec<Insulation> getCodec()
    {
        if (codec == null)
        {
            codec = Codec.either(StaticInsulation.CODEC, AdaptiveInsulation.CODEC).xmap(
                    either -> either.map(stat -> stat, adaptive -> adaptive),
                    insulation -> insulation instanceof StaticInsulation stat
                            ? Either.left(stat)
                            : Either.right((AdaptiveInsulation) insulation)
            );
        }
        return codec;
    }

    public abstract boolean isEmpty();

    /**
     * Split insulation into values that fit individual insulation slots.
     */
    public abstract List<Insulation> split();

    public abstract double getCold();

    public abstract double getHeat();

    public abstract double getValue();

    public abstract <T extends Insulation> T copy();

    public static List<Insulation> deepCopy(List<Insulation> list)
    {
        List<Insulation> copy = new ArrayList<>(list.size());
        for (Insulation insulation : list)
        {
            copy.add(insulation.copy());
        }
        return copy;
    }

    public static List<Insulation> splitList(List<Insulation> values)
    {
        List<Insulation> split = new ArrayList<>();
        for (Insulation insulation : values)
        {
            split.addAll(insulation.split());
        }
        return split;
    }

    public static List<Insulation> combine(List<Insulation> list1, List<Insulation> list2)
    {
        List<Insulation> combined = new ArrayList<>();
        List<Insulation> remaining = new ArrayList<>(list2);

        for (Insulation first : list1)
        {
            if (first instanceof StaticInsulation staticFirst)
            {
                boolean matched = false;
                for (int i = 0; i < remaining.size(); i++)
                {
                    Insulation second = remaining.get(i);
                    if (second instanceof StaticInsulation staticSecond
                            && combineStaticInsulations(staticFirst, staticSecond, combined))
                    {
                        remaining.remove(i);
                        matched = true;
                        break;
                    }
                }
                if (!matched)
                {
                    combined.add(staticFirst);
                }
            }
            else
            {
                combined.add(first);
            }
        }

        combined.addAll(remaining);
        combined.removeIf(Insulation::isEmpty);
        return combined;
    }

    private static boolean combineStaticInsulations(StaticInsulation first,
                                                     StaticInsulation second,
                                                     List<Insulation> result)
    {
        double cold1 = first.getCold();
        double heat1 = first.getHeat();
        double cold2 = second.getCold();
        double heat2 = second.getHeat();

        if (cold1 > 0 && heat2 > 0)
        {
            combineValues(cold1, heat2, result);
            return true;
        }
        if (heat1 > 0 && cold2 > 0)
        {
            combineValues(cold2, heat1, result);
            return true;
        }
        return false;
    }

    private static void combineValues(double coldValue, double heatValue, List<Insulation> result)
    {
        if (coldValue == heatValue)
        {
            result.add(new StaticInsulation(coldValue, heatValue));
            return;
        }

        double neutral = Math.min(coldValue, heatValue);
        result.add(new StaticInsulation(neutral, neutral));

        if (coldValue > neutral)
        {
            result.add(new StaticInsulation(coldValue - neutral, 0));
        }
        if (heatValue > neutral)
        {
            result.add(new StaticInsulation(0, heatValue - neutral));
        }
    }

    /**
     * Sort cold, neutral, heat, then adaptive insulation without modifying the input list.
     */
    public static List<Insulation> sort(List<Insulation> values)
    {
        List<Insulation> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparingInt(Insulation::getCompareValue));
        return sorted;
    }

    public int getCompareValue()
    {
        List<Insulation> subset = split();
        if (subset.size() > 1)
        {
            return sort(subset).getFirst().getCompareValue() - 10;
        }
        if (this instanceof AdaptiveInsulation adaptive)
        {
            return Math.abs(adaptive.getInsulation()) >= 2 ? 7 : 8;
        }
        if (this instanceof StaticInsulation stat)
        {
            double cold = Math.abs(stat.getCold());
            double heat = Math.abs(stat.getHeat());
            if (cold > heat)
            {
                return cold >= 2 ? 1 : 2;
            }
            if (cold == heat)
            {
                return cold >= 1 ? 3 : 4;
            }
            return heat >= 2 ? 5 : 6;
        }
        return 0;
    }

    private static <E extends Enum<E> & StringRepresentable> Codec<E> enumCodec(E[] values)
    {
        return Codec.STRING.comapFlatMap(name ->
        {
            for (E value : values)
            {
                if (value.getSerializedName().equalsIgnoreCase(name))
                {
                    return DataResult.success(value);
                }
            }
            return DataResult.error(() -> "Unknown enum value: " + name);
        }, StringRepresentable::getSerializedName);
    }

    public enum Slot implements StringRepresentable
    {
        ITEM("item"),
        CURIO("curio"),
        ARMOR("armor");

        public static final Codec<Slot> CODEC = enumCodec(values());

        private final String name;

        Slot(String name)
        {
            this.name = name;
        }

        @Override
        public String getSerializedName()
        {
            return name;
        }

        public static Slot byName(String name)
        {
            for (Slot value : values())
            {
                if (value.name.equalsIgnoreCase(name))
                {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown value for enum Slot: " + name);
        }
    }

    public enum Type implements StringRepresentable
    {
        COLD("cold"),
        HEAT("heat"),
        NEUTRAL("neutral"),
        ADAPTIVE("adaptive");

        public static final Codec<Type> CODEC = enumCodec(values());

        private final String name;

        Type(String name)
        {
            this.name = name;
        }

        @Override
        public String getSerializedName()
        {
            return name;
        }

        public static Type byName(String name)
        {
            for (Type value : values())
            {
                if (value.name.equalsIgnoreCase(name))
                {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown value for enum Type: " + name);
        }
    }
}
