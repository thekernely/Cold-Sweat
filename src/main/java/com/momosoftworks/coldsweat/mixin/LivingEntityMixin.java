package com.momosoftworks.coldsweat.mixin;

import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureEffectRuntime;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Small loader bridge for gameplay hooks Fabric does not expose directly.
 *
 * Keeping these hooks here lets the actual Cold Sweat temperature-effect math
 * remain loader-independent and testable.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin
{
    @ModifyVariable(
            method = "heal(F)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private float coldSweat$limitColdHealing(float healAmount)
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
