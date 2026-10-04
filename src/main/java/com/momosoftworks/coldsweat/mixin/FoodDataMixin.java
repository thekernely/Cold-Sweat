package com.momosoftworks.coldsweat.mixin;

import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureEffectRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the exact scope in which vanilla hunger/saturation regeneration is
 * allowed to call LivingEntity.heal.
 *
 * Direct/magical healing is deliberately outside this scope.
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin
{
    @Inject(
            method = "tick",
            at = @At("HEAD")
    )
    private void coldSweat$beginNaturalRegeneration(
            ServerPlayer player,
            CallbackInfo ci
    )
    {
        TemperatureEffectRuntime.beginNaturalRegeneration(player);
    }

    @Inject(
            method = "tick",
            at = @At("RETURN")
    )
    private void coldSweat$endNaturalRegeneration(
            ServerPlayer player,
            CallbackInfo ci
    )
    {
        TemperatureEffectRuntime.endNaturalRegeneration(player);
    }
}
