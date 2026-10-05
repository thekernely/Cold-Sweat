package com.momosoftworks.coldsweat.api.registry;

import com.momosoftworks.coldsweat.api.temperature.modifier.FoodTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.SoulSproutTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureModifierRuntime;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureRuntime;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Loader-independent boundary for temperature-affecting items.
 *
 * M8.10b narrows Waterskins to an explicit one-use thermal drink. Carrying a
 * filled Waterskin no longer changes CORE and stored water no longer decays in
 * inventory. Warm/Very Warm drinks instead apply one visible, non-stacking
 * Warming effect whose heat is fed gradually into the existing CORE runtime.
 */
public final class ItemTemperatureRegistry
{
    public static final double WARM_WATER_THRESHOLD_C = 27.5;
    public static final double VERY_WARM_WATER_THRESHOLD_C = 40.0;
    public static final double HEATED_WATERSKIN_C = 50.0;

    public static final int WARM_WATERSKIN_DURATION_TICKS = 20 * 20;
    public static final int VERY_WARM_WATERSKIN_DURATION_TICKS = 30 * 20;

    private static final double WARM_WATERSKIN_TOTAL_C = 0.35;
    private static final double VERY_WARM_WATERSKIN_TOTAL_C = 0.80;
    private static final double WARM_WATERSKIN_CORE_CAP_C = 35.7;
    private static final double VERY_WARM_WATERSKIN_CORE_CAP_C = 36.0;

    private static final Map<Item, List<FoodTemperature>> FOOD_TEMPERATURES =
            new IdentityHashMap<>();

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
     * Apply the player-facing Warming state for a consumed Waterskin.
     *
     * Cold water intentionally has no thermal effect. Warm is Warming I and
     * Very Warm is Warming II. A weaker drink never downgrades a stronger
     * active effect; equal/stronger drinks refresh/replace through vanilla's
     * normal MobEffectInstance semantics.
     */
    public static void applyWaterskinDrink(
            LivingEntity entity,
            double waterCelsius
    )
    {
        int amplifier;
        int duration;
        double capCelsius;

        if (waterCelsius >= VERY_WARM_WATER_THRESHOLD_C)
        {
            amplifier = 1;
            duration = VERY_WARM_WATERSKIN_DURATION_TICKS;
            capCelsius = VERY_WARM_WATERSKIN_CORE_CAP_C;
        }
        else if (waterCelsius >= WARM_WATER_THRESHOLD_C)
        {
            amplifier = 0;
            duration = WARM_WATERSKIN_DURATION_TICKS;
            capCelsius = WARM_WATERSKIN_CORE_CAP_C;
        }
        else
        {
            return;
        }

        double currentCoreCelsius =
                TemperatureRuntime.bodyStressToCelsius(
                        Temperature.get(
                                entity,
                                Temperature.Trait.CORE
                        )
                );

        /*
         * No pre-buffing: once the body is already above this Waterskin's
         * recovery ceiling, the drink still hydrates but creates no stored
         * warming state that could activate later.
         */
        if (currentCoreCelsius >= capCelsius)
        {
            return;
        }

        MobEffectInstance current =
                entity.getEffect(ModEffects.WARMING);

        if (current != null
                && current.getAmplifier() > amplifier)
        {
            return;
        }

        entity.addEffect(
                new MobEffectInstance(
                        ModEffects.WARMING,
                        duration,
                        amplifier,
                        false,
                        false,
                        true
                )
        );
    }

    private static void tickServer(net.minecraft.server.MinecraftServer server)
    {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            tickWaterskinWarming(player);
        }
    }

    /**
     * Feed the visible Warming state into the existing normalized CORE model.
     * The environment continues to act on CORE at the same time, so clothing
     * and shelter determine how much of this emergency heat the player keeps.
     */
    private static void tickWaterskinWarming(ServerPlayer player)
    {
        MobEffectInstance warming =
                player.getEffect(ModEffects.WARMING);

        if (warming == null)
        {
            return;
        }

        boolean veryWarm = warming.getAmplifier() >= 1;
        int fullDuration = veryWarm
                ? VERY_WARM_WATERSKIN_DURATION_TICKS
                : WARM_WATERSKIN_DURATION_TICKS;

        double capCelsius = veryWarm
                ? VERY_WARM_WATERSKIN_CORE_CAP_C
                : WARM_WATERSKIN_CORE_CAP_C;

        double totalCelsius = veryWarm
                ? VERY_WARM_WATERSKIN_TOTAL_C
                : WARM_WATERSKIN_TOTAL_C;

        double coreStress =
                Temperature.get(
                        player,
                        Temperature.Trait.CORE
                );

        double coreCelsius =
                TemperatureRuntime.bodyStressToCelsius(
                        coreStress
                );

        if (coreCelsius >= capCelsius)
        {
            player.removeEffect(ModEffects.WARMING);
            return;
        }

        double deltaCelsius =
                totalCelsius / fullDuration;

        double nextCelsius =
                Math.min(
                        capCelsius,
                        coreCelsius + deltaCelsius
                );

        Temperature.set(
                player,
                Temperature.Trait.CORE,
                TemperatureRuntime.celsiusToBodyStress(
                        nextCelsius
                )
        );

        /*
         * Immediate feedback only: a few tiny warm sparks during roughly the
         * first second after drinking/refreshing. The effect itself has no
         * vanilla potion particles, so this never turns into a 30-second swirl.
         */
        if (warming.getDuration() > fullDuration - 20
                && player.tickCount % 4 == 0)
        {
            ServerLevel serverLevel = (ServerLevel) player.level();
            serverLevel.sendParticles(
                    ParticleTypes.SMALL_FLAME,
                    player.getX(),
                    player.getY() + player.getBbHeight() * 0.55,
                    player.getZ(),
                    1,
                    0.18,
                    0.20,
                    0.18,
                    0.01
            );
        }
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
                "Initialized {} temperature-affecting consumable definition(s) and Waterskin warming runtime.",
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
