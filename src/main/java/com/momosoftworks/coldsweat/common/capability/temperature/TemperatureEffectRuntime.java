package com.momosoftworks.coldsweat.common.capability.temperature;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.TemperatureEffectSettings;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side temperature gameplay effects that can be expressed cleanly with
 * vanilla 26.2 attributes.
 *
 * Locked M7 cold model:
 * - impairment begins at ~35 C and reaches full strength at ~33 C;
 * - up to 50% of normal health becomes unavailable to food-based natural
 *   regeneration as hypothermia deepens;
 * - below 33 C food regeneration collapses quickly;
 * - at and below 32 C food/saturation regeneration is fully disabled.
 *
 * Locked M7 heat model:
 * - heat symptoms begin at ~39.5 C;
 * - direct damage starts at 41 C;
 * - food-based natural regeneration collapses from 41 -> 42 C;
 * - at and above 42 C food/saturation regeneration is fully disabled.
 *
 * Direct/magical healing remains available on both sides as an emergency
 * resource. Food cannot substitute for external rewarming or cooling.
 */
public final class TemperatureEffectRuntime
{
    private static final Identifier FREEZE_MOVEMENT =
            ColdSweatFabric.id("freeze_movement_speed");

    private static final Identifier FREEZE_MINING =
            ColdSweatFabric.id("freeze_mining_speed");

    /**
     * Marks the Player whose FoodData.tick is currently executing.
     *
     * LivingEntity.heal is used by many mechanics, so the distinction is
     * important: only vanilla food/saturation natural regeneration should be
     * suppressed by critical body temperatures. Potions, regeneration effects,
     * golden apples, commands, and other direct healing remain emergency
     * resources.
     */
    private static final ThreadLocal<Player> NATURAL_REGEN_PLAYER =
            new ThreadLocal<>();

    public static void applyServerEffects(LivingEntity entity)
    {
        if (!(entity instanceof Player player))
        {
            return;
        }

        double coldFactor = getColdEffectFactor(player);

        applyMovementPenalty(
                player,
                coldFactor
        );

        applyMiningPenalty(
                player,
                coldFactor
        );
    }

    public static void clear(LivingEntity entity)
    {
        if (!(entity instanceof Player player))
        {
            return;
        }

        removeModifier(
                player.getAttribute(Attributes.MOVEMENT_SPEED),
                FREEZE_MOVEMENT
        );

        removeModifier(
                player.getAttribute(Attributes.BLOCK_BREAK_SPEED),
                FREEZE_MINING
        );
    }

    /**
     * Existing balance preserved in physiological terms:
     * - factor 0 at ~35 C core
     * - factor 1 at ~33 C core
     * - cold resistance blends the effect back toward zero
     * - Ice Resistance fully nullifies cold effects
     */
    public static double getColdEffectFactor(LivingEntity entity)
    {
        if (entity.isSpectator()
                || (entity instanceof Player player
                    && player.isCreative()))
        {
            return 0.0;
        }

        if (entity.hasEffect(ModEffects.ICE_RESISTANCE))
        {
            return 0.0;
        }

        double bodyTemperature =
                Temperature.get(
                        entity,
                        Temperature.Trait.BODY
                );

        double rawFactor =
                TemperatureRuntime.coldImpairmentFactor(
                        bodyTemperature
                );

        if (rawFactor <= 0.0)
        {
            return 0.0;
        }

        double resistance =
                clamp(
                        Temperature.get(
                                entity,
                                Temperature.Trait.COLD_RESISTANCE
                        ),
                        0.0,
                        1.0
                );

        return rawFactor * (1.0 - resistance);
    }

    /**
     * Continuous amount of health capacity currently frozen out of vanilla
     * food-based natural regeneration.
     *
     * Deliberately not rounded: the HUD can show frost creeping across the
     * boundary heart continuously instead of jumping in half-heart chunks.
     */
    public static double getFrozenHealth(LivingEntity entity)
    {
        if (!(entity instanceof Player))
        {
            return 0.0;
        }

        double effectFactor =
                getColdEffectFactor(entity);

        if (effectFactor <= 0.0
                || TemperatureEffectSettings.HEARTS_FREEZING_PERCENTAGE <= 0.0)
        {
            return 0.0;
        }

        return entity.getMaxHealth()
                * TemperatureEffectSettings.HEARTS_FREEZING_PERCENTAGE
                * effectFactor;
    }

    /**
     * Called by FoodDataMixin for the duration of vanilla FoodData.tick.
     */
    public static void beginNaturalRegeneration(Player player)
    {
        NATURAL_REGEN_PLAYER.set(player);
    }

    public static void endNaturalRegeneration(Player player)
    {
        if (NATURAL_REGEN_PLAYER.get() == player)
        {
            NATURAL_REGEN_PLAYER.remove();
        }
    }

    /**
     * Limits ONLY food/saturation natural regeneration.
     *
     * Cold:
     * 33.0 C -> 100%
     * 32.5 C -> 25%
     * 32.2 C -> 4%
     * 32.0 C -> 0%
     *
     * Heat:
     * 41.0 C -> 100%
     * 41.5 C -> 25%
     * 41.8 C -> 4%
     * 42.0 C -> 0%
     *
     * Direct/magical healing is deliberately outside this restriction.
     */
    public static float limitHealing(
            LivingEntity entity,
            float healAmount
    )
    {
        if (healAmount <= 0.0F
                || !(entity instanceof Player player)
                || NATURAL_REGEN_PLAYER.get() != player)
        {
            return healAmount;
        }

        if (player.isCreative()
                || player.isSpectator())
        {
            return healAmount;
        }

        double bodyStress =
                Temperature.get(
                        player,
                        Temperature.Trait.BODY
                );

        double coreCelsius =
                TemperatureRuntime.bodyStressToCelsius(
                        bodyStress
                );

        double coldRegenFactor =
                player.hasEffect(ModEffects.ICE_RESISTANCE)
                        ? 1.0
                        : coldNaturalRegenerationFactor(
                                coreCelsius
                        );

        double heatRegenFactor =
                player.hasEffect(MobEffects.FIRE_RESISTANCE)
                        ? 1.0
                        : heatNaturalRegenerationFactor(
                                coreCelsius
                        );

        double regenFactor =
                Math.min(
                        coldRegenFactor,
                        heatRegenFactor
                );

        if (regenFactor <= 0.0)
        {
            return 0.0F;
        }

        float scaledHeal =
                (float) (
                        healAmount
                                * regenFactor
                );

        /*
         * Frozen health is a cold-only mechanic. At hot core temperatures
         * getFrozenHealth naturally returns zero.
         */
        double frozenHealth =
                getFrozenHealth(player);

        float unfrozenHealth =
                (float) (
                        player.getMaxHealth()
                                - frozenHealth
                );

        float remainingNaturalCapacity =
                Math.max(
                        0.0F,
                        unfrozenHealth
                                - player.getHealth()
                );

        return Math.max(
                0.0F,
                Math.min(
                        scaledHeal,
                        remainingNaturalCapacity
                )
        );
    }

    public static double coldNaturalRegenerationFactor(
            double coreCelsius
    )
    {
        if (coreCelsius >= 33.0)
        {
            return 1.0;
        }

        if (coreCelsius <= 32.0)
        {
            return 0.0;
        }

        double normalized =
                clamp(
                        (coreCelsius - 32.0)
                                / 1.0,
                        0.0,
                        1.0
                );

        return normalized * normalized;
    }

    public static double heatNaturalRegenerationFactor(
            double coreCelsius
    )
    {
        if (coreCelsius <= 41.0)
        {
            return 1.0;
        }

        if (coreCelsius >= 42.0)
        {
            return 0.0;
        }

        double normalized =
                clamp(
                        (42.0 - coreCelsius)
                                / 1.0,
                        0.0,
                        1.0
                );

        return normalized * normalized;
    }

    public static double reduceOutgoingKnockback(
            LivingEntity attacker,
            double strength
    )
    {
        if (attacker == null
                || strength <= 0.0
                || TemperatureEffectSettings.COLD_KNOCKBACK_REDUCTION <= 0.0)
        {
            return strength;
        }

        double effectFactor =
                getColdEffectFactor(attacker);

        if (effectFactor <= 0.0)
        {
            return strength;
        }

        double reduction =
                clamp(
                        TemperatureEffectSettings.COLD_KNOCKBACK_REDUCTION
                                * effectFactor,
                        0.0,
                        1.0
                );

        return strength * (1.0 - reduction);
    }

    private static void applyMovementPenalty(
            Player player,
            double effectFactor
    )
    {
        AttributeInstance movement =
                player.getAttribute(
                        Attributes.MOVEMENT_SPEED
                );

        if (movement == null)
        {
            return;
        }

        if (effectFactor <= 0.0
                || TemperatureEffectSettings.COLD_MOVEMENT_SLOWDOWN <= 0.0)
        {
            movement.removeModifier(
                    FREEZE_MOVEMENT
            );
            return;
        }

        double penalty =
                TemperatureEffectSettings.COLD_MOVEMENT_SLOWDOWN
                        * effectFactor;

        if (player.isSprinting()
                && !player.onGround())
        {
            penalty *= 1.5;
        }

        penalty = clamp(
                penalty,
                0.0,
                1.0
        );

        movement.addOrUpdateTransientModifier(
                new AttributeModifier(
                        FREEZE_MOVEMENT,
                        -penalty,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                )
        );
    }

    private static void applyMiningPenalty(
            Player player,
            double effectFactor
    )
    {
        AttributeInstance blockBreakSpeed =
                player.getAttribute(
                        Attributes.BLOCK_BREAK_SPEED
                );

        if (blockBreakSpeed == null)
        {
            return;
        }

        if (effectFactor <= 0.0
                || TemperatureEffectSettings.COLD_MINING_IMPAIRMENT <= 0.0)
        {
            blockBreakSpeed.removeModifier(
                    FREEZE_MINING
            );
            return;
        }

        double penalty =
                clamp(
                        TemperatureEffectSettings.COLD_MINING_IMPAIRMENT
                                * effectFactor,
                        0.0,
                        1.0
                );

        blockBreakSpeed.addOrUpdateTransientModifier(
                new AttributeModifier(
                        FREEZE_MINING,
                        -penalty,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                )
        );
    }

    private static void removeModifier(
            AttributeInstance attribute,
            Identifier id
    )
    {
        if (attribute != null)
        {
            attribute.removeModifier(id);
        }
    }

    private static double clamp(
            double value,
            double min,
            double max
    )
    {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    private TemperatureEffectRuntime()
    {
    }
}
