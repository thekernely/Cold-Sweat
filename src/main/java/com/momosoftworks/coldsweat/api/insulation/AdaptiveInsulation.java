package com.momosoftworks.coldsweat.api.insulation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Insulation that gradually adapts toward cold or heat protection according to
 * the surrounding world temperature.
 */
public class AdaptiveInsulation extends Insulation
{
    public static final Codec<AdaptiveInsulation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Insulation.DOUBLE_CODEC.fieldOf("value").forGetter(AdaptiveInsulation::getInsulation),
            Insulation.DOUBLE_CODEC.optionalFieldOf("factor", 0d).forGetter(AdaptiveInsulation::getFactor),
            Insulation.DOUBLE_CODEC.fieldOf("adapt_speed").forGetter(AdaptiveInsulation::getSpeed)
    ).apply(instance, AdaptiveInsulation::new));

    private final double insulation;
    private final double speed;
    private double factor;

    public AdaptiveInsulation(double insulation, double speed)
    {
        this(insulation, 0d, speed);
    }

    public AdaptiveInsulation(double insulation, double factor, double speed)
    {
        this.insulation = insulation;
        this.factor = factor;
        this.speed = speed;
    }

    public static double calculateChange(AdaptiveInsulation insulation,
                                         double worldTemp,
                                         double minTemp,
                                         double maxTemp)
    {
        double factor = insulation.getFactor();
        double adaptSpeed = insulation.getSpeed();
        double tempFactor = blend(-1d, 1d, worldTemp, minTemp, maxTemp);

        if (tempFactor >= -0.5d && tempFactor <= 0.5d)
        {
            return shrink(factor, adaptSpeed);
        }

        if (Math.signum(factor) != Math.signum(tempFactor))
        {
            adaptSpeed *= 2d;
        }
        return clamp(factor + adaptSpeed * Math.signum(tempFactor), -1d, 1d);
    }

    /**
     * The scalar adaptation component was already ported in M2, so the model can
     * preserve upstream armor adaptation state without pulling sewn-insulation
     * capability/config code into this slice.
     */
    public static double getFactorFromArmor(ItemStack stack)
    {
        return stack.getOrDefault(ModItemComponents.ARMOR_ADAPTATION, 0d);
    }

    public static void setFactorToArmor(ItemStack stack, double factor)
    {
        stack.set(ModItemComponents.ARMOR_ADAPTATION, factor);
    }

    public static void readFactorFromArmor(AdaptiveInsulation insulation, ItemStack stack)
    {
        double storedFactor = getFactorFromArmor(stack);
        if (storedFactor != 0d)
        {
            insulation.setFactor(storedFactor);
        }
    }

    @Override
    public double getValue()
    {
        return insulation;
    }

    public double getInsulation()
    {
        return insulation;
    }

    public double getFactor()
    {
        return factor;
    }

    public void setFactor(double factor)
    {
        this.factor = factor;
    }

    public double getSpeed()
    {
        return speed;
    }

    @Override
    public double getCold()
    {
        return blend(insulation * 0.75d, 0d, factor, -1d, 1d);
    }

    @Override
    public double getHeat()
    {
        return blend(0d, insulation * 0.75d, factor, -1d, 1d);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Insulation> T copy()
    {
        return (T) new AdaptiveInsulation(insulation, factor, speed);
    }

    @Override
    public boolean isEmpty()
    {
        return insulation == 0d;
    }

    @Override
    public List<Insulation> split()
    {
        List<Insulation> split = new ArrayList<>();
        int slots = (int) Math.ceil(Math.abs(insulation) / 2d);
        for (int i = 0; i < slots; i++)
        {
            double value = minAbs(shrink(insulation, i * 2d), 2d * Math.signum(insulation));
            split.add(new AdaptiveInsulation(value, factor, speed));
        }
        return split;
    }

    private static double blend(double from, double to, double value, double rangeMin, double rangeMax)
    {
        if (rangeMin > rangeMax)
        {
            return blend(to, from, value, rangeMax, rangeMin);
        }
        if (value <= rangeMin)
        {
            return from;
        }
        if (value >= rangeMax)
        {
            return to;
        }
        return (to - from) / (rangeMax - rangeMin) * (value - rangeMin) + from;
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static double shrink(double value, double amount)
    {
        return Math.max(0d, Math.abs(value) - amount) * Math.signum(value);
    }

    private static double minAbs(double first, double second)
    {
        return Math.abs(second) < Math.abs(first) ? second : first;
    }

    @Override
    public String toString()
    {
        return "AdaptiveInsulation{" + "insulation=" + insulation + ", factor=" + factor + ", speed=" + speed + '}';
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        return obj instanceof AdaptiveInsulation adaptive
                && insulation == adaptive.insulation
                && factor == adaptive.factor
                && speed == adaptive.speed;
    }

    @Override
    public int hashCode()
    {
        int result = Double.hashCode(insulation);
        result = 31 * result + Double.hashCode(factor);
        result = 31 * result + Double.hashCode(speed);
        return result;
    }
}
