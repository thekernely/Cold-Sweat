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
 * Client-only effects (blur, fog, sway, vignette, shiver, frozen-heart HUD)
 * intentionally wait for M7.
 */
public final class TemperatureEffectRuntime
{
    private static final Identifier FREEZE_MOVEMENT =
            ColdSweatFabric.id("freeze_movement_speed");

    private static final Identifier FREEZE_MINING =
            ColdSweatFabric.id("freeze_mining_speed");

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
     * Equivalent to the default player's Cold Sweat effect range:
     * - factor 0 at BODY -50
     * - factor 1 at BODY -100
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

        double start =
                TemperatureEffectSettings.COLD_EFFECT_START;

        double maximum =
                TemperatureEffectSettings.COLD_EFFECT_MAX;

        if (bodyTemperature >= start)
        {
            return 0.0;
        }

        double rawFactor =
                clamp(
                        (start - bodyTemperature)
                                / (start - maximum),
                        0.0,
                        1.0
                );

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

        /*
         * Preserve upstream's extra movement penalty while sprinting in the
         * air, while expressing the final result as a 26.2 transient attribute.
         */
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
