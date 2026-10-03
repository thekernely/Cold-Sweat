package com.momosoftworks.coldsweat.common.capability.insulation;

import com.momosoftworks.coldsweat.api.insulation.AdaptiveInsulation;
import com.momosoftworks.coldsweat.api.insulation.Insulation;
import com.momosoftworks.coldsweat.api.registry.InsulationRegistry;
import com.momosoftworks.coldsweat.api.temperature.modifier.ArmorInsulationTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.handler.ItemInsulationManager;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import com.momosoftworks.coldsweat.fabric.temperature.ThermoregulationRuntime;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Calculates equipped armor insulation and applies it to the RATE trait.
 *
 * M7.12h inserts the high-inertia thermoregulation transform before the
 * existing insulation transform. RATE attributes still resolve first in
 * EntityTempManager, so custom RATE modifiers remain part of the environmental
 * pressure that reaches this layer.
 */
public final class ArmorInsulationRuntime
{
    private static final Map<LivingEntity, CachedInsulation> CACHE =
            new WeakHashMap<>();

    public static double applyRate(LivingEntity entity, double rate)
    {
        if (!(entity instanceof Player player) || player.isSpectator())
        {
            return rate;
        }

        /*
         * Legacy environmental RATE is now a stress/demand signal, not a raw
         * CORE delta. Convert it to slow physiological drift first; equipment
         * insulation then reduces that remaining transfer exactly where it did
         * before.
         */
        double regulatedRate =
                ThermoregulationRuntime.applyEnvironmentalRate(
                        player,
                        rate
                );

        CachedInsulation cached = CACHE.get(player);
        if (cached == null || player.tickCount % 20 == 0)
        {
            cached = calculate(player);
            CACHE.put(player, cached);
        }

        if (cached.cold <= 0.0 && cached.heat <= 0.0)
        {
            return regulatedRate;
        }

        return cached.modifier.update(
                regulatedRate,
                player,
                Temperature.Trait.RATE
        );
    }

    private static CachedInsulation calculate(Player player)
    {
        double cold = 0.0;
        double heat = 0.0;

        double worldTemperature = Temperature.get(player, Temperature.Trait.WORLD);
        double freezingPoint = Temperature.get(player, Temperature.Trait.FREEZING_POINT);
        double burningPoint = Temperature.get(player, Temperature.Trait.BURNING_POINT);

        for (EquipmentSlot slot : new EquipmentSlot[]
        {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        })
        {
            ItemStack armorStack = player.getItemBySlot(slot);
            for (Insulation insulation : InsulationRegistry.getArmorInsulation(armorStack))
            {
                if (insulation instanceof AdaptiveInsulation adaptive)
                {
                    AdaptiveInsulation.readFactorFromArmor(adaptive, armorStack);
                    double newFactor = AdaptiveInsulation.calculateChange(
                            adaptive,
                            worldTemperature,
                            freezingPoint,
                            burningPoint
                    );
                    AdaptiveInsulation.setFactorToArmor(armorStack, newFactor);
                    adaptive.setFactor(newFactor);
                }

                cold += insulation.getCold();
                heat += insulation.getHeat();
            }

            // Sewn insulation stores adaptive state inside the armor component.
            var sewnCap = ItemInsulationManager.getExistingInsulationCap(armorStack);
            if (sewnCap.isPresent())
            {
                ItemInsulationCap adaptedCap = sewnCap.get().adapt(
                        worldTemperature,
                        freezingPoint,
                        burningPoint
                );
                if (adaptedCap != sewnCap.get())
                {
                    armorStack.set(ModItemComponents.ARMOR_INSULATION, adaptedCap);
                }

                for (Insulation insulation : adaptedCap.getInsulators())
                {
                    cold += insulation.getCold();
                    heat += insulation.getHeat();
                }
            }
        }

        /*
         * ProcessEquipmentInsulation also treats normal armor protection as
         * cold/heat protection. Upstream caps each equipped piece at 20 armor;
         * an aggregate cap of 80 preserves that ceiling while avoiding the old
         * ItemStack attribute-modifier API removed by 26.2.
         */
        double armorProtection = Math.min(
                Math.max(0.0, player.getAttributeValue(Attributes.ARMOR)),
                80.0
        );
        cold += armorProtection;
        heat += armorProtection;

        return new CachedInsulation(
                cold,
                heat,
                new ArmorInsulationTempModifier(cold, heat).tickRate(20)
        );
    }

    private record CachedInsulation(
            double cold,
            double heat,
            ArmorInsulationTempModifier modifier
    )
    {
    }

    private ArmorInsulationRuntime()
    {
    }
}
