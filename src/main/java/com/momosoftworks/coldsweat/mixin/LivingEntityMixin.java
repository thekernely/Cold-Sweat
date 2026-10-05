package com.momosoftworks.coldsweat.mixin;

import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureEffectRuntime;
import com.momosoftworks.coldsweat.fabric.hydration.FoodHydrationRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Small loader bridge for gameplay hooks Fabric does not expose directly.
 *
 * The healing hook is context-aware: TemperatureEffectRuntime only modifies
 * healing while FoodData.tick is actively performing vanilla natural
 * regeneration. Direct/magical healing passes through unchanged.
 *
 * M8.7 also observes the canonical completed-item-use path. Only explicitly
 * registered food ids receive hydration; no display-name or item-name
 * heuristics are used.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin
{
    @Shadow
    protected ItemStack useItem;

    @Inject(
            method = "completeUsingItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private void coldSweat$applyFoodHydration(
            CallbackInfo ci
    )
    {
        if ((Object) this instanceof ServerPlayer player)
        {
            FoodHydrationRegistry.applyConsumedFood(
                    player,
                    this.useItem
            );
        }
    }

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
