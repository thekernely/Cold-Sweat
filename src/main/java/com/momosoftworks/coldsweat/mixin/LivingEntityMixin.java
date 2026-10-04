package com.momosoftworks.coldsweat.mixin;

import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureEffectRuntime;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Small loader bridge for gameplay hooks Fabric does not expose directly.
 *
 * The healing hook is context-aware: TemperatureEffectRuntime only modifies
 * healing while FoodData.tick is actively performing vanilla natural
 * regeneration. Direct/magical healing passes through unchanged.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin
{
    @ModifyVariable(
            method = "heal(F)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private float coldSweat$limitColdNaturalHealing(float healAmount)
    {
        LivingEntity entity =
                (LivingEntity) (Object) this;

        return TemperatureEffectRuntime.limitHealing(
                entity,
                healAmount
        );
    }

    @ModifyVariable(
            method = "knockback",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private double coldSweat$reduceColdKnockback(double strength)
    {
        LivingEntity target =
                (LivingEntity) (Object) this;

        LivingEntity attacker =
                target.getLastHurtByMob();

        return TemperatureEffectRuntime.reduceOutgoingKnockback(
                attacker,
                strength
        );
    }
}
