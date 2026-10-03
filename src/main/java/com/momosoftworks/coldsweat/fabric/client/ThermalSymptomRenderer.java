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

        if (player == null
                || player.isSpectator()
                || player.isCreative())
        {
            return;
        }

        double coreCelsius =
                TemperatureHudData.capture(player)
                        .bodyCelsius();

        int effectLevel =
                TemperatureRuntime.bodyVisualEffectLevel(
                        coreCelsius
                );

        if (effectLevel <= 0)
        {
            return;
        }

        boolean cold =
                coreCelsius
                        < TemperatureRuntime.NORMAL_BODY_C;

        double pulse =
                effectLevel >= 2
                        ? 0.90
                            + 0.10
                            * Math.sin(
                                    player.tickCount * 0.22
                            )
                        : 1.0;

        /*
         * Keep this intentionally subtle. The effect should tell the player
         * that physiology is becoming dangerous without obscuring play or
         * competing with shaders/resource packs.
         */
        double opacity =
                (effectLevel == 1
                        ? 0.045
                        : 0.095)
                * pulse;

        drawEdgeVignette(
                graphics,
                cold ? COLD_RGB : HEAT_RGB,
                opacity,
                effectLevel
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
