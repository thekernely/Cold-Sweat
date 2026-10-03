package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureRuntime;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * M7.12j first client-side physiological symptom pass.
 *
 * This renderer deliberately reacts to internal/core physiology rather than
 * directly to WORLD. A player standing in a brutal environment therefore does
 * not instantly receive a screen effect while homeostasis is still coping.
 * Only meaningful core displacement activates the vignette.
 *
 * The first pass is intentionally conservative: a cheap edge tint only. Camera
 * shake, blur/fog, frozen-heart artwork, and other higher-risk presentation
 * hooks remain separate follow-up slices.
 */
public final class ThermalSymptomRenderer
{
    private static final int COLD_RGB = 0x6EAFFF;
    private static final int HEAT_RGB = 0xFF7B42;

    private static final int VIGNETTE_LAYERS = 8;

    /*
     * Per-frame camera offset state for shivering. We apply only the delta
     * between the previous and current target offset so the effect oscillates
     * around the player's real aim instead of accumulating rotational drift.
     */
    private static LocalPlayer shiverPlayer;
    private static double lastShiverYawOffset;

    private ThermalSymptomRenderer()
    {
    }

    public static void register()
    {
        HudElementRegistry.addLast(
                ColdSweatFabric.id("thermal_symptom_vignette"),
                ThermalSymptomRenderer::render
        );
    }

    private static void render(
            GuiGraphicsExtractor graphics,
            net.minecraft.client.DeltaTracker deltaTracker
    )
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null)
        {
            shiverPlayer = null;
            lastShiverYawOffset = 0.0;
            return;
        }

        if (player.isSpectator()
                || player.isCreative()
                || minecraft.isPaused())
        {
            clearShiver(player);
            return;
        }

        double coreCelsius =
                TemperatureHudData.capture(player)
                        .bodyCelsius();

        /*
         * Shivering is deliberately physiological, just like the vignette.
         * A brutally cold WORLD value does not shake the camera while the core
         * is still successfully defended. Once core temperature begins falling
         * into the symptom range, shivering fades in continuously.
         */
        applyShiver(
                player,
                coreCelsius
        );

        boolean cold =
                coreCelsius
                        < TemperatureRuntime.NORMAL_BODY_C;

        double opacity =
                cold
                        ? coldOpacity(coreCelsius)
                        : heatOpacity(coreCelsius);

        if (opacity <= 0.0)
        {
            return;
        }

        /*
         * Pulse is also continuous. It fades in only once the player reaches
         * the severe physiological range, avoiding a visible step when crossing
         * the stage boundary.
         */
        double severeProgress =
                cold
                        ? clamp01((33.5 - coreCelsius) / 0.5)
                        : clamp01((coreCelsius - 41.0) / 1.0);

        double pulseDepth =
                0.08 * severeProgress;

        double pulse =
                1.0
                        - pulseDepth
                        + pulseDepth
                        * Math.sin(
                                player.tickCount * 0.22
                        );

        int effectLevel =
                severeProgress > 0.0
                        ? 2
                        : 1;

        drawEdgeVignette(
                graphics,
                cold ? COLD_RGB : HEAT_RGB,
                opacity * pulse,
                effectLevel
        );
    }

    /**
     * Smooth, intermittent cold shiver.
     *
     * 35 C is the onset. From there:
     * - amplitude rises continuously as core temperature falls;
     * - bursts become more frequent and last longer;
     * - at ~33 C the shiver is pronounced but still small enough that aiming
     *   remains possible.
     *
     * Rendering runs every frame, so the motion is smooth rather than a 20 Hz
     * tick-step. System.nanoTime is animation-only and has no simulation role.
     */
    private static void applyShiver(
            LocalPlayer player,
            double coreCelsius
    )
    {
        double severity =
                clamp01(
                        (35.0 - coreCelsius) / 2.0
                );

        if (severity <= 0.0)
        {
            clearShiver(player);
            return;
        }

        if (shiverPlayer != player)
        {
            shiverPlayer = player;
            lastShiverYawOffset = 0.0;
        }

        double seconds =
                System.nanoTime()
                        / 1_000_000_000.0;

        /*
         * M7.12k.1: the first pass was too subtle above ~34 C.
         *
         * Keep the same smooth physiological onset at 35 C, but make the
         * early phase perceptible to the naked eye. The response is
         * intentionally front-loaded so shivering becomes a useful warning
         * before deep hypothermia.
         */
        double perceptualSeverity =
                Math.sqrt(severity);

        double envelopeFrequency =
                lerp(
                        0.48,
                        0.78,
                        perceptualSeverity
                );

        double envelopeWave =
                0.5
                        + 0.5
                        * Math.sin(
                                seconds
                                        * Math.PI
                                        * 2.0
                                        * envelopeFrequency
                        );

        double dutyThreshold =
                lerp(
                        0.72,
                        0.06,
                        perceptualSeverity
                );

        double envelope =
                smoothstep(
                        dutyThreshold,
                        1.0,
                        envelopeWave
                );

        double amplitudeDegrees =
                lerp(
                        0.045,
                        0.30,
                        perceptualSeverity
                );

        double shiverFrequency =
                lerp(
                        7.0,
                        11.5,
                        perceptualSeverity
                );

        double targetOffset =
                Math.sin(
                        seconds
                                * Math.PI
                                * 2.0
                                * shiverFrequency
                )
                        * amplitudeDegrees
                        * envelope;

        double delta =
                targetOffset
                        - lastShiverYawOffset;

        if (Math.abs(delta) > 1.0e-7)
        {
            player.setYRot(
                    (float) (
                            player.getYRot()
                                    + delta
                    )
            );
        }

        lastShiverYawOffset =
                targetOffset;
    }

    private static void clearShiver(LocalPlayer player)
    {
        if (shiverPlayer == player
                && Math.abs(lastShiverYawOffset) > 1.0e-7)
        {
            player.setYRot(
                    (float) (
                            player.getYRot()
                                    - lastShiverYawOffset
                    )
            );
        }

        shiverPlayer = player;
        lastShiverYawOffset = 0.0;
    }

    private static double smoothstep(
            double edge0,
            double edge1,
            double value
    )
    {
        if (edge1 <= edge0)
        {
            return value >= edge1
                    ? 1.0
                    : 0.0;
        }

        double t =
                clamp01(
                        (value - edge0)
                                / (edge1 - edge0)
                );

        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Continuous cold vignette curve.
     *
     * 35.0 C -> 6.75%
     * 34.0 C -> 16%
     * 33.5 C -> 26%
     * 33.0 C -> 38%
     *
     * The onset is ~1.5x stronger than M7.12j, while every intermediate
     * temperature is interpolated smoothly rather than snapping between stages.
     */
    private static double coldOpacity(double coreCelsius)
    {
        if (coreCelsius > 35.0)
        {
            return 0.0;
        }

        if (coreCelsius >= 34.0)
        {
            return lerp(
                    0.0675,
                    0.16,
                    (35.0 - coreCelsius) / 1.0
            );
        }

        if (coreCelsius >= 33.5)
        {
            return lerp(
                    0.16,
                    0.26,
                    (34.0 - coreCelsius) / 0.5
            );
        }

        return lerp(
                0.26,
                0.38,
                clamp01((33.5 - coreCelsius) / 0.5)
        );
    }

    /**
     * Heat mirrors the cold curve with physiological heat thresholds.
     *
     * 39.5 C -> 6.75%
     * 40.5 C -> 16%
     * 41.0 C -> 26%
     * 42.0 C -> 38%
     */
    private static double heatOpacity(double coreCelsius)
    {
        if (coreCelsius < 39.5)
        {
            return 0.0;
        }

        if (coreCelsius <= 40.5)
        {
            return lerp(
                    0.0675,
                    0.16,
                    (coreCelsius - 39.5) / 1.0
            );
        }

        if (coreCelsius <= 41.0)
        {
            return lerp(
                    0.16,
                    0.26,
                    (coreCelsius - 40.5) / 0.5
            );
        }

        return lerp(
                0.26,
                0.38,
                clamp01((coreCelsius - 41.0) / 1.0)
        );
    }

    private static double lerp(
            double start,
            double end,
            double delta
    )
    {
        return start
                + (end - start)
                * clamp01(delta);
    }

    private static double clamp01(double value)
    {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    private static void drawEdgeVignette(
            GuiGraphicsExtractor graphics,
            int rgb,
            double opacity,
            int effectLevel
    )
    {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        int thickness = Math.max(
                14,
                Math.min(
                        42,
                        Math.min(width, height)
                                / (effectLevel >= 2 ? 6 : 8)
                )
        );

        int step = Math.max(
                1,
                thickness / VIGNETTE_LAYERS
        );

        for (int layer = 0;
             layer < VIGNETTE_LAYERS;
             layer++)
        {
            int offset = layer * step;
            if (offset >= thickness)
            {
                break;
            }

            int band = Math.min(
                    step,
                    thickness - offset
            );

            double normalized =
                    1.0
                            - layer
                            / (double) VIGNETTE_LAYERS;

            double falloff =
                    normalized * normalized;

            int alpha = (int) Math.round(
                    255.0
                            * opacity
                            * falloff
            );

            if (alpha <= 0)
            {
                continue;
            }

            int color =
                    (alpha << 24)
                            | (rgb & 0x00FFFFFF);

            int left = offset;
            int top = offset;
            int right = width - offset;
            int bottom = height - offset;

            if (right <= left || bottom <= top)
            {
                break;
            }

            /* Top and bottom strips. */
            graphics.fill(
                    left,
                    top,
                    right,
                    Math.min(bottom, top + band),
                    color
            );

            graphics.fill(
                    left,
                    Math.max(top, bottom - band),
                    right,
                    bottom,
                    color
            );

            /*
             * Side strips exclude the just-drawn corners so alpha does not
             * stack there and make the corners disproportionately opaque.
             */
            int sideTop = Math.min(
                    bottom,
                    top + band
            );

            int sideBottom = Math.max(
                    sideTop,
                    bottom - band
            );

            if (sideBottom > sideTop)
            {
                graphics.fill(
                        left,
                        sideTop,
                        Math.min(right, left + band),
                        sideBottom,
                        color
                );

                graphics.fill(
                        Math.max(left, right - band),
                        sideTop,
                        right,
                        sideBottom,
                        color
                );
            }
        }
    }
}
