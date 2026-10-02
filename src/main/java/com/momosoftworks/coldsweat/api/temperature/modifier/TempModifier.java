package com.momosoftworks.coldsweat.api.temperature.modifier;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Core temperature-modifier runtime.
 *
 * This is the loader-independent portion of upstream Cold Sweat's TempModifier.
 * Registry/codec/event-bus integration is restored separately once the modifier
 * registry itself is ported.
 */
public abstract class TempModifier
{
    private int expireTicks = -1;
    private int ticksExisted = 0;
    private int tickRate = 1;

    private final Double[] lastInput = new Double[Temperature.Trait.values().length];
    private final Double[] lastOutput = new Double[Temperature.Trait.values().length];

    @SuppressWarnings("unchecked")
    private final Function<Double, Double>[] function =
            new Function[Temperature.Trait.values().length];

    private boolean changed = false;

    public TempModifier()
    {
    }

    @SuppressWarnings("unchecked")
    public final <T extends TempModifier> T tickRate(int interval)
    {
        tickRate = Math.max(1, interval);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public final <T extends TempModifier> T expires(int ticks)
    {
        expireTicks = ticks;
        return (T) this;
    }

    /**
     * Calculates the transformation this modifier applies for one trait.
     * This should only be recalculated when the modifier's tick rate requires it.
     */
    protected abstract Function<Double, Double> calculate(
            LivingEntity entity,
            Temperature.Trait trait
    );

    public void tick(LivingEntity entity)
    {
    }

    /**
     * Recalculate and cache this modifier's function, then apply it.
     */
    public final double update(
            double temperature,
            LivingEntity entity,
            Temperature.Trait trait
    )
    {
        setFunction(trait, calculate(entity, trait));
        return apply(trait, temperature);
    }

    /**
     * Apply the currently cached function without recalculating it.
     */
    public double apply(Temperature.Trait trait, double temperature)
    {
        setLastInput(trait, temperature);
        double output = getFunction(trait).apply(temperature);
        setLastOutput(trait, output);
        return output;
    }

    public void onAdded(LivingEntity entity, Temperature.Trait trait)
    {
    }

    public void onRemoved(LivingEntity entity, Temperature.Trait trait)
    {
    }

    public void onSiblingAdded(
            LivingEntity entity,
            Temperature.Trait trait,
            TempModifier sibling
    )
    {
    }

    public void onSiblingRemoved(
            LivingEntity entity,
            Temperature.Trait trait,
            TempModifier sibling
    )
    {
    }

    public final int getExpireTime()
    {
        return expireTicks;
    }

    public final int getTicksExisted()
    {
        return ticksExisted;
    }

    public final int setTicksExisted(int ticks)
    {
        return ticksExisted = ticks;
    }

    public final int getTickRate()
    {
        return tickRate;
    }

    public final Function<Double, Double> getFunction(Temperature.Trait trait)
    {
        Function<Double, Double> current = function[trait.ordinal()];
        if (current == null)
        {
            current = value -> value;
            setFunction(trait, current);
        }
        return current;
    }

    protected final void setFunction(
            Temperature.Trait trait,
            Function<Double, Double> newFunction
    )
    {
        function[trait.ordinal()] = newFunction != null ? newFunction : value -> value;
    }

    public final double getLastInput(Temperature.Trait trait)
    {
        Double value = lastInput[trait.ordinal()];
        return value != null ? value : 0.0;
    }

    protected final void setLastInput(Temperature.Trait trait, double temperature)
    {
        lastInput[trait.ordinal()] = temperature;
    }

    public final double getLastOutput(Temperature.Trait trait)
    {
        Double value = lastOutput[trait.ordinal()];
        return value != null ? value : 0.0;
    }

    protected final void setLastOutput(Temperature.Trait trait, double temperature)
    {
        lastOutput[trait.ordinal()] = temperature;
    }

    public void markDirty()
    {
        changed = true;
    }

    public boolean isDirty()
    {
        return changed;
    }

    public void markClean()
    {
        changed = false;
    }
}
