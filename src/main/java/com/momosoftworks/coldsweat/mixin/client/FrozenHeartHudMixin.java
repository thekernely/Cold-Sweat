package com.momosoftworks.coldsweat.mixin.client;

import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureEffectRuntime;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Progressive frozen-heart overlay for Minecraft 26.2.
 *
 * Important: Hud.HeartType is private in 26.2, so this mixin intentionally
 * hooks extractHearts(...) at TAIL and reproduces only the public coordinate
 * math needed for the frost overlay. It never references the private enum.
 */
@Mixin(Hud.class)
public abstract class FrozenHeartHudMixin
{
    @Unique
    private static final Identifier COLD_SWEAT$FROZEN_HEARTS =
            ColdSweatFabric.id(
                    "textures/gui/overlay/hearts_frozen.png"
            );

    @Shadow
    private int tickCount;

    @Inject(
            method = "extractHearts",
            at = @At("TAIL")
    )
    private void coldSweat$extractFrozenHeartOverlay(
            GuiGraphicsExtractor graphics,
            Player player,
            int xLeft,
            int yLineBase,
            int healthRowHeight,
            int heartOffsetIndex,
            float maxHealth,
            int currentHealth,
            int oldHealth,
            int absorption,
            boolean blink,
            CallbackInfo ci
    )
    {
        if (player == null
                || player.isCreative()
                || player.isSpectator())
        {
            return;
        }

        double frozenHealth =
                TemperatureEffectRuntime.getFrozenHealth(player);

        if (frozenHealth <= 0.0)
        {
            return;
        }

        int healthContainerCount =
                (int) Math.ceil(maxHealth / 2.0F);

        int absorptionContainerCount =
                (int) Math.ceil(absorption / 2.0F);

        int totalContainerCount =
                healthContainerCount
                        + absorptionContainerCount;

        boolean lowHealthJitter =
                currentHealth + absorption <= 4;

        /*
         * Vanilla seeds Hud.random immediately before extractHearts with:
         * tickCount * 312871.
         *
         * Use our own RNG with the same seed so we can reproduce the exact
         * 0/1px low-health jitter without consuming or disturbing vanilla's
         * private RNG state.
         */
        RandomSource jitterRandom =
                RandomSource.create();

        jitterRandom.setSeed(
                (long) this.tickCount * 312871L
        );

        boolean hardcore =
                player.level()
                        .getLevelData()
                        .isHardcore();

        /*
         * Vanilla iterates all health + absorption containers from right to
         * left. We mirror that order so low-health RNG consumption and heart
         * positions remain aligned exactly.
         */
        for (int containerIndex =
                        totalContainerCount - 1;
             containerIndex >= 0;
             containerIndex--)
        {
            int row = containerIndex / 10;
            int column = containerIndex % 10;

            int x =
                    xLeft + column * 8;

            int y =
                    yLineBase
                            - row * healthRowHeight;

            if (lowHealthJitter)
            {
                y += jitterRandom.nextInt(2);
            }

            /*
             * Regeneration raises one normal heart by 2px. Absorption hearts
             * never receive this offset in vanilla.
             */
            if (containerIndex < healthContainerCount
                    && containerIndex == heartOffsetIndex)
            {
                y -= 2;
            }

            /*
             * Frozen-health capacity applies only to normal health, not
             * absorption hearts. We still walked absorption containers above
             * so RNG/jitter stays synchronized with vanilla.
             */
            if (containerIndex >= healthContainerCount)
            {
                continue;
            }

            /*
             * Frozen capacity grows from the right edge of the normal health
             * bar toward the left, matching Cold Sweat's existing behavior.
             */
            int heartFromRight =
                    healthContainerCount
                            - containerIndex;

            double frozenInThisHeart =
                    clamp(
                            frozenHealth
                                    - (heartFromRight - 1) * 2.0,
                            0.0,
                            2.0
                    );

            if (frozenInThisHeart <= 0.0)
            {
                continue;
            }

            double frozenFraction =
                    frozenInThisHeart / 2.0;

            /*
             * The upstream texture stores 7x7 frozen-heart sprites in a
             * 21x28 sheet:
             *   x=0  normal
             *   x=7  hardcore
             *   x=14 container
             *
             * Crop from the right side so the boundary heart behaves like a
             * continuous frost progress bar instead of jumping in half-heart
             * steps.
             */
            int frostPixels =
                    Math.max(
                            1,
                            Math.min(
                                    7,
                                    (int) Math.ceil(
                                            frozenFraction * 7.0
                                    )
                            )
                    );

            int cropOffset =
                    7 - frostPixels;

            /*
             * First frost the heart container itself. This keeps unavailable
             * capacity visible even when the actual health in that slot is
             * currently missing.
             */
            drawFrozenSlice(
                    graphics,
                    x,
                    y,
                    14,
                    0,
                    cropOffset,
                    frostPixels
            );

            /*
             * If this heart currently contains health, frost the filled heart
             * too. The red vanilla heart remains underneath and the blue frost
             * visually creeps over it.
             */
            int healthHalfIndex =
                    containerIndex * 2;

            if (healthHalfIndex < currentHealth)
            {
                boolean halfHeart =
                        healthHalfIndex + 1
                                == currentHealth;

                drawFrozenSlice(
                        graphics,
                        x,
                        y,
                        hardcore ? 7 : 0,
                        halfHeart ? 7 : 0,
                        cropOffset,
                        frostPixels
                );
            }
        }
    }

    @Unique
    private static void drawFrozenSlice(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int sourceU,
            int sourceV,
            int cropOffset,
            int width
    )
    {
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                COLD_SWEAT$FROZEN_HEARTS,
                x + 1 + cropOffset,
                y + 1,
                sourceU + cropOffset,
                sourceV,
                width,
                7,
                21,
                28
        );
    }

    @Unique
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
}
