package com.momosoftworks.coldsweat.mixin;

import com.momosoftworks.coldsweat.fabric.ecology.CropClimateRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Generic natural-growth gate.
 *
 * <p>Injecting at BlockState's random-tick dispatch keeps the integration
 * content-agnostic: vanilla crops, Farmer's Delight crops, and third-party
 * crops can opt in through the Cold Sweat block tag without class imports.
 *
 * <p>Ecliptic wraps the downstream Block.randomTick invocation. Therefore a
 * Cold Sweat denial stops the attempt first, while an allowed attempt still
 * reaches Ecliptic's own season/humidity decision.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CropGrowthMixin
{
    @Shadow
    protected abstract BlockState asState();

    @Inject(
            method = "randomTick",
            at = @At("HEAD"),
            cancellable = true
    )
    private void coldSweat$gateColdCropGrowth(
            ServerLevel level,
            BlockPos pos,
            RandomSource random,
            CallbackInfo ci
    )
    {
        BlockState state =
                asState();

        if (!CropClimateRuntime.allowNaturalGrowthTick(
                level,
                pos,
                state,
                random
        ))
        {
            ci.cancel();
        }
    }
}
