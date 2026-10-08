package com.momosoftworks.coldsweat.mixin;

import com.momosoftworks.coldsweat.fabric.ecology.CropClimateExposure;
import com.momosoftworks.coldsweat.fabric.ecology.CropClimateRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Generic cold-sensitive crop tick gate, including fully-grown plants.
 *
 * <p>Normal Minecraft crop growth stops ticking at maturity. Frost exposure
 * must not stop at maturity: harvesting late should carry a real winter risk.
 * Only affected CropBlock states and pitcher LOWER halves are awakened; wild
 * vegetation, pitcher tops, sugar cane, and bamboo are deliberately excluded.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CropGrowthMixin
{
    @Shadow
    protected abstract BlockState asState();

    @Inject(
            method = "isRandomlyTicking",
            at = @At("RETURN"),
            cancellable = true
    )
    private void coldSweat$observeMatureFrostCrops(CallbackInfoReturnable<Boolean> cir)
    {
        // Avoid tag lookups for almost all block states. If vanilla already
        // ticks this block, the existing crop growth hook handles it.
        if (cir.getReturnValueZ()) return;

        BlockState state = asState();
        boolean cropFamily = state.getBlock() instanceof CropBlock;
        boolean pitcherRoot = state.is(Blocks.PITCHER_CROP)
                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;

        if ((cropFamily || pitcherRoot) && CropClimateExposure.isAffectedCrop(state))
        {
            cir.setReturnValue(true);
        }
    }

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
        BlockState state = asState();
        if (!CropClimateRuntime.allowNaturalGrowthTick(level, pos, state, random))
        {
            ci.cancel();
        }
    }
}
