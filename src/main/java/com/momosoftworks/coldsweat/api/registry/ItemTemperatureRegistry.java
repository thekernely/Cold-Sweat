package com.momosoftworks.coldsweat.api.registry;

import com.momosoftworks.coldsweat.api.temperature.modifier.FoodTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.SoulSproutTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.WaterTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.WaterskinTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureModifierRuntime;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Loader-independent boundary for temperature-affecting items.
 *
 * Upstream fills this data through FoodData/ItemTempData/ConfigSettings. Pulling
 * that full requirement/value-getter graph forward would couple M5 to the later
 * general config port, so Fabric keeps the built-in defaults here. The registry
 * can later be populated by the full config loader without changing gameplay.
 */
public final class ItemTemperatureRegistry
{
    private static final Map<Item, List<FoodTemperature>> FOOD_TEMPERATURES =
            new IdentityHashMap<>();

    private static final Map<LivingEntity, List<WaterskinTempModifier>> ACTIVE_WATERSKIN_EFFECTS =
            new WeakHashMap<>();

    private static boolean initialized;

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

    /**
     * Upstream waterskins apply their consumed temperature gradually to CORE
     * for 100 ticks rather than changing body temperature instantaneously.
     */
    public static void applyWaterskinDrink(
            LivingEntity entity,
            double waterTemperature
    )
    {
        if (Double.compare(waterTemperature, 0.0) == 0)
        {
            return;
        }

        WaterskinTempModifier modifier =
                new WaterskinTempModifier(waterTemperature / 100.0)
                        .expires(100);

        modifier.onAdded(entity, Temperature.Trait.CORE);
        ACTIVE_WATERSKIN_EFFECTS
                .computeIfAbsent(entity, ignored -> new ArrayList<>())
                .add(modifier);
    }

    /**
     * Crouch-pouring a waterskin replaces the player's current WaterTempModifier
     * with a small hot/cold wetness seed, matching upstream's sign behavior.
     */
    public static void applyWaterskinPour(
            Player player,
            double waterTemperature
    )
    {
        List<TempModifier> worldModifiers =
                TemperatureModifierRuntime.getWorldModifiers(player);

        if (worldModifiers.isEmpty())
        {
            return;
        }

        double sign = waterTemperature < 0.0 ? -1.0 : 1.0;
        WaterTempModifier replacement =
                new WaterTempModifier(0.05 * sign).tickRate(5);

        for (int i = 0; i < worldModifiers.size(); i++)
        {
            TempModifier modifier = worldModifiers.get(i);
            if (modifier instanceof WaterTempModifier)
            {
                modifier.onRemoved(player, Temperature.Trait.WORLD);
                replacement.onAdded(player, Temperature.Trait.WORLD);
                worldModifiers.set(i, replacement);
                return;
            }
        }

        replacement.onAdded(player, Temperature.Trait.WORLD);
        worldModifiers.add(replacement);
    }

    private static void tickServer(net.minecraft.server.MinecraftServer server)
    {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            tickInventoryTemperatures(player);
            tickWaterskinEffects(player);
        }

        ACTIVE_WATERSKIN_EFFECTS.keySet().removeIf(
                entity -> entity == null || entity.isRemoved()
        );
    }

    /**
     * Upstream built-in ItemTempData for filled waterskins:
     * - +/-0.025 CORE per tick while in hand/hotbar
     * - active only while water temperature is outside +/-0.1
     * - water neutralizes by 0.1 every five ticks while actively carried
     */
    private static void tickInventoryTemperatures(ServerPlayer player)
    {
        double coreEffect = 0.0;
        IdentityHashMap<ItemStack, Boolean> visited = new IdentityHashMap<>();

        for (int slot = 0; slot < 9; slot++)
        {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && visited.put(stack, Boolean.TRUE) == null)
            {
                coreEffect += getWaterskinInventoryEffect(player, stack);
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && visited.put(offhand, Boolean.TRUE) == null)
        {
            coreEffect += getWaterskinInventoryEffect(player, offhand);
        }

        if (Double.compare(coreEffect, 0.0) != 0)
        {
            Temperature.add(
                    player,
                    Temperature.Trait.CORE,
                    coreEffect
            );
        }
    }

    private static double getWaterskinInventoryEffect(
            ServerPlayer player,
            ItemStack stack
    )
    {
        if (!stack.is(ModItems.FILLED_WATERSKIN))
        {
            return 0.0;
        }

        double temperature = stack.getOrDefault(
                ModItemComponents.WATER_TEMPERATURE,
                0.0
        );

        double effect = temperature > 0.1
                ? 0.025
                : temperature < -0.1
                    ? -0.025
                    : 0.0;

        if (effect != 0.0 && player.tickCount % 5 == 0)
        {
            stack.set(
                    ModItemComponents.WATER_TEMPERATURE,
                    shrinkTowardZero(temperature, 0.1)
            );
        }

        return effect * stack.getCount();
    }

    private static void tickWaterskinEffects(LivingEntity entity)
    {
        List<WaterskinTempModifier> modifiers =
                ACTIVE_WATERSKIN_EFFECTS.get(entity);

        if (modifiers == null)
        {
            return;
        }

        for (int i = 0; i < modifiers.size(); i++)
        {
            WaterskinTempModifier modifier = modifiers.get(i);

            if (modifier.getTicksExisted() % modifier.getTickRate() == 0)
            {
                modifier.tick(entity);
            }

            double delta = modifier.update(
                    0.0,
                    entity,
                    Temperature.Trait.CORE
            );

            if (!Double.isNaN(delta) && Double.compare(delta, 0.0) != 0)
            {
                Temperature.add(entity, Temperature.Trait.CORE, delta);
            }

            modifier.setTicksExisted(modifier.getTicksExisted() + 1);
            int expireTime = modifier.getExpireTime();
            if (expireTime != -1
                    && modifier.getTicksExisted() > expireTime)
            {
                modifier.onRemoved(entity, Temperature.Trait.CORE);
                modifiers.remove(i);
                i--;
            }
        }

        if (modifiers.isEmpty())
        {
            ACTIVE_WATERSKIN_EFFECTS.remove(entity);
        }
    }

    private static double shrinkTowardZero(double value, double amount)
    {
        return Math.max(0.0, Math.abs(value) - amount) * Math.signum(value);
    }

    public static void initialize()
    {
        if (initialized)
        {
            return;
        }
        initialized = true;

        ServerTickEvents.END_SERVER_TICK.register(ItemTemperatureRegistry::tickServer);

        ColdSweatFabric.LOGGER.info(
                "Initialized {} temperature-affecting consumable definition(s) and waterskin item runtime.",
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
