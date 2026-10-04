package com.momosoftworks.coldsweat.common.capability.temperature;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.TemperatureEffectSettings;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side temperature gameplay effects that can be expressed cleanly with
 * vanilla 26.2 attributes.
 *
 * M7 cold model:
 * - impairment begins at ~35 C and reaches full strength at ~33 C;
 * - up to 50% of normal health becomes unavailable to food-based natural
 *   regeneration as hypothermia deepens;
 * - below 33 C food regeneration collapses quickly;
 * - at and below 32 C food/saturation regeneration is fully disabled;
 * - direct/magical healing is not treated as food regeneration.
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
     * suppressed by the locked hypothermia model. Potions, regeneration
     * effects, golden apples, commands, and other direct healing remain
     * emergency resources.
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
     * 33.0 C -> 100% of otherwise-allowed food regen
     * 32.5 C -> 25%
     * 32.2 C -> 4%
     * 32.0 C -> 0%
     *
     * The squared curve intentionally collapses fast. Food can buy time in the
     * early critical band, but it cannot replace external heat.
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
                || player.isSpectator()
                || player.hasEffect(ModEffects.ICE_RESISTANCE))
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

        double regenFactor =
                coldNaturalRegenerationFactor(
                        coreCelsius
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
