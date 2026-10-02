package com.momosoftworks.coldsweat.api.event.common.temperautre;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fabric-native replacement for Cold Sweat's cancellable NeoForge
 * TemperatureChangedEvent.
 *
 * Listeners may cancel the mutation or replace the proposed temperature value.
 * The misspelled "temperautre" package is intentionally preserved for upstream
 * source/API compatibility.
 */
public final class TemperatureChangedEvent
{
    @FunctionalInterface
    public interface Callback
    {
        void onTemperatureChanged(TemperatureChangedEvent event);
    }

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(
            Callback.class,
            listeners -> event ->
            {
                for (Callback listener : listeners)
                {
                    listener.onTemperatureChanged(event);
                }
            }
    );

    private final LivingEntity entity;
    private final Temperature.Trait trait;
    private final double oldTemperature;
    private double temperature;
    private boolean canceled;

    public TemperatureChangedEvent(
            LivingEntity entity,
            Temperature.Trait trait,
            double oldTemperature,
            double temperature
    )
    {
        this.entity = entity;
        this.trait = trait;
        this.oldTemperature = oldTemperature;
        this.temperature = temperature;
    }

    public static TemperatureChangedEvent fire(
            LivingEntity entity,
            Temperature.Trait trait,
            double oldTemperature,
            double temperature
    )
    {
        TemperatureChangedEvent event =
                new TemperatureChangedEvent(entity, trait, oldTemperature, temperature);
        EVENT.invoker().onTemperatureChanged(event);
        return event;
    }

    public LivingEntity getEntity()
    {
        return entity;
    }

    public Temperature.Trait getTrait()
    {
        return trait;
    }

    public double getOldTemperature()
    {
        return oldTemperature;
    }

    public double getTemperature()
    {
        return temperature;
    }

    public void setTemperature(double temperature)
    {
        this.temperature = temperature;
    }

    public boolean isCanceled()
    {
        return canceled;
    }

    public void setCanceled(boolean canceled)
    {
        this.canceled = canceled;
    }
}
