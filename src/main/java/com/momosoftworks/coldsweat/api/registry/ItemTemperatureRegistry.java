package com.momosoftworks.coldsweat.api.registry;

import com.momosoftworks.coldsweat.api.temperature.modifier.FoodTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.SoulSproutTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureModifierRuntime;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Loader-independent boundary for temperature-affecting consumables.
 *
 * Upstream fills this data through FoodData/ConfigSettings. Pulling that full
 * requirement/value-getter graph forward would couple M5 to the later general
 * config port, so the Fabric runtime keeps the built-in defaults here. The
 * registry can be populated by the full config loader later without changing
 * item/runtime behavior.
 */
public final class ItemTemperatureRegistry
{
    private static final Map<Item, List<FoodTemperature>> FOOD_TEMPERATURES =
            new IdentityHashMap<>();

    static
    {
        // Upstream ItemSettingsConfig default.
        registerFood(ModItems.SOUL_SPROUT, -20.0, 1200, 1);
    }

    public static void registerFood(
            Item item,
            double temperature,
            int duration,
            int stackLimit
    )
    {
        FOOD_TEMPERATURES
                .computeIfAbsent(item, ignored -> new ArrayList<>())
                .add(new FoodTemperature(
                        temperature,
                        Math.max(0, duration),
                        Math.max(1, stackLimit)
                ));
    }

    public static void applyConsumed(ServerPlayer player, Item item)
    {
        List<FoodTemperature> definitions = FOOD_TEMPERATURES.get(item);
        if (definitions == null || definitions.isEmpty())
        {
            return;
        }

        for (FoodTemperature definition : definitions)
        {
            applyFoodTemperature(player, item, definition);
        }
    }

    private static void applyFoodTemperature(
            ServerPlayer player,
            Item item,
            FoodTemperature definition
    )
    {
        /*
         * Upstream applies zero-duration foods to CORE as a one-shot modifier.
         * The standalone runtime has no persistent CORE modifier list, so the
         * equivalent mutation is applied directly.
         */
        if (definition.duration() <= 0)
        {
            Temperature.add(
                    player,
                    Temperature.Trait.CORE,
                    definition.temperature()
            );
            return;
        }

        List<TempModifier> modifiers =
                TemperatureModifierRuntime.getBaseModifiers(player);

        int matching = 0;
        FoodTempModifier firstMatch = null;

        for (TempModifier modifier : modifiers)
        {
            if (modifier instanceof FoodTempModifier food
                    && food.matches(item, definition.temperature()))
            {
                matching++;
                if (firstMatch == null)
                {
                    firstMatch = food;
                }
            }
        }

        /*
         * Placement.limitDuplicates(...).orElse(REPLACE FIRST) parity:
         * once the stack limit is reached, replace the oldest matching effect
         * instead of allowing an unbounded stack.
         */
        if (matching >= definition.stackLimit() && firstMatch != null)
        {
            Iterator<TempModifier> iterator = modifiers.iterator();
            while (iterator.hasNext())
            {
                TempModifier modifier = iterator.next();
                if (modifier == firstMatch)
                {
                    modifier.onRemoved(player, Temperature.Trait.BASE);
                    iterator.remove();
                    break;
                }
            }
        }

        FoodTempModifier modifier = item == ModItems.SOUL_SPROUT
                ? new SoulSproutTempModifier(item, definition.temperature())
                : new FoodTempModifier(item, definition.temperature());

        modifier.expires(definition.duration())
                .tickRate(definition.duration());
        modifier.onAdded(player, Temperature.Trait.BASE);
        modifiers.add(modifier);
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info(
                "Initialized {} temperature-affecting consumable definition(s).",
                FOOD_TEMPERATURES.values().stream().mapToInt(List::size).sum()
        );
    }

    public record FoodTemperature(
            double temperature,
            int duration,
            int stackLimit
    )
    {
    }

    private ItemTemperatureRegistry()
    {
    }
}
