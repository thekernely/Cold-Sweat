package com.momosoftworks.coldsweat.api.insulation;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed cold/heat insulation values.
 */
public class StaticInsulation extends Insulation
{
    public static final Codec<StaticInsulation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Insulation.DOUBLE_CODEC.fieldOf("cold").forGetter(StaticInsulation::getCold),
            Insulation.DOUBLE_CODEC.fieldOf("heat").forGetter(StaticInsulation::getHeat)
    ).apply(instance, StaticInsulation::new));

    private final double cold;
    private final double heat;

    public StaticInsulation(double cold, double heat)
    {
        this.cold = cold;
        this.heat = heat;
    }

    public StaticInsulation(Pair<? extends Number, ? extends Number> pair)
    {
        this(pair.getFirst().doubleValue(), pair.getSecond().doubleValue());
    }

    @Override
    public double getCold()
    {
        return cold;
    }

    @Override
    public double getHeat()
    {
        return heat;
    }

    @Override
    public double getValue()
    {
        return cold + heat;
    }

    @Override
    public boolean isEmpty()
    {
        return cold == 0 && heat == 0;
    }

    @Override
    public List<Insulation> split()
    {
        List<Insulation> insulation = new ArrayList<>();
        double cold = this.cold;
        double heat = this.heat;
        double neutral = (cold > 0) == (heat > 0) ? minAbs(cold, heat) : 0;

        cold -= neutral;
        heat -= neutral;

        int coldSlots = (int) Math.ceil(Math.abs(cold) / 2d);
        for (int i = 0; i < coldSlots; i++)
        {
            double coldInsulation = minAbs(shrink(cold, i * 2d), 2d * Math.signum(cold));
            insulation.add(new StaticInsulation(coldInsulation, 0d));
        }

        int neutralSlots = (int) Math.ceil(Math.abs(neutral));
        for (int i = 0; i < neutralSlots; i++)
        {
            double neutralInsulation = minAbs(shrink(neutral, i), Math.signum(neutral));
            insulation.add(new StaticInsulation(neutralInsulation, neutralInsulation));
        }

        int heatSlots = (int) Math.ceil(Math.abs(heat) / 2d);
        for (int i = 0; i < heatSlots; i++)
        {
            double heatInsulation = minAbs(shrink(heat, i * 2d), 2d * Math.signum(heat));
            insulation.add(new StaticInsulation(0d, heatInsulation));
        }
        return insulation;
    }

    private static double shrink(double value, double amount)
    {
        return Math.max(0, Math.abs(value) - amount) * Math.signum(value);
    }

    private static double minAbs(double first, double second)
    {
        return Math.abs(second) < Math.abs(first) ? second : first;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Insulation> T copy()
    {
        return (T) new StaticInsulation(cold, heat);
    }

    @Override
    public String toString()
    {
        return "StaticInsulation{" + "cold=" + cold + ", heat=" + heat + '}';
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        return obj instanceof StaticInsulation insulation
                && cold == insulation.cold
                && heat == insulation.heat;
    }

    @Override
    public int hashCode()
    {
        return Double.hashCode(cold) * 31 + Double.hashCode(heat);
    }
}
