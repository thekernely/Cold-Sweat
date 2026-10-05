package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.api.util.Hydration;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * M8.8 dehydration presentation.
 *
 * Conservative by design: no blur, nausea distortion, or permanent
 * full-screen filter. At <=4 hydration a warm-dark edge pulse appears every
 * few seconds. At <=1 it becomes stronger/more frequent and adds a tiny camera
 * drift during the pulse.
 */
public final class DehydrationSymptomRenderer
{
    private static final int DEHYDRATION_RGB = 0x2B231D;
    private static final int VIGNETTE_LAYERS = 8;

    private static LocalPlayer swayPlayer;
    private static double lastYawOffset;

    private DehydrationSymptomRenderer()
    {
    }

    public static void register()
    {
        HudElementRegistry.addLast(
                ColdSweatFabric.id("dehydration_symptom_vignette"),
                DehydrationSymptomRenderer::render
        );
    }

    private static void render(
            GuiGraphicsExtractor graphics,
            net.minecraft.client.DeltaTracker deltaTracker
    )
    {
        Minecraft minecraft =
                Minecraft.getInstance();

        LocalPlayer player =
                minecraft.player;

        if (player == null)
        {
            swayPlayer = null;
            lastYawOffset = 0.0;
            return;
        }

        if (player.isCreative()
                || player.isSpectator()
                || minecraft.isPaused())
        {
            clearSway(player);
            return;
        }

        double hydration =
                Math.max(
                        0.0,
                        Math.min(
                                Hydration.MAX_HYDRATION,
                                Hydration.get(player)
                        )
                );

        if (hydration > 4.0)
        {
            clearSway(player);
            return;
        }

        double severity =
                clamp01(
                        (4.0 - hydration)
                                / 4.0
                );

        boolean critical =
                hydration <= 1.0;

        int periodTicks =
                critical
                        ? 70
                        : 120;

        double phase =
                Math.floorMod(
                        player.tickCount,
                        periodTicks
                )
                        / (double) periodTicks;

        double wave =
                0.5
                        + 0.5
                        * Math.sin(
                                phase
                                        * Math.PI
                                        * 2.0
                        );

        double envelope =
                smoothstep(
                        0.72,
                        1.0,
                        wave
                );

        double baseOpacity =
                critical
                        ? 0.0225
                        : 0.0;

        double pulseOpacity =
                0.04
                        + 0.09
                        * severity;

        double opacity =
                baseOpacity
                        + pulseOpacity
                        * envelope;

        if (opacity > 0.001)
        {
            drawEdgeVignette(
                    graphics,
                    DEHYDRATION_RGB,
                    opacity,
                    critical ? 2 : 1
            );
        }

        applyCriticalSway(
                player,
                hydration,
                envelope
        );
    }

    private static void applyCriticalSway(
            LocalPlayer player,
            double hydration,
            double envelope
    )
    {
        if (hydration > 1.0)
        {
            clearSway(player);
            return;
        }

        if (swayPlayer != player)
        {
            swayPlayer = player;
            lastYawOffset = 0.0;
        }

        double criticalSeverity =
                clamp01(
                        1.0 - hydration
                );

        double amplitudeDegrees =
                0.025
                        + 0.035
                        * criticalSeverity;

        double seconds =
                System.nanoTime()
                        / 1_000_000_000.0;

        double targetOffset =
                Math.sin(
                        seconds
                                * Math.PI
                                * 2.0
                                * 1.15
                )
                        * amplitudeDegrees
                        * envelope;

        double delta =
                targetOffset
                        - lastYawOffset;

        if (Math.abs(delta) > 1.0e-7)
        {
            player.setYRot(
                    (float) (
                            player.getYRot()
                                    + delta
                    )
            );
        }

        lastYawOffset =
                targetOffset;
    }

    private static void clearSway(
            LocalPlayer player
    )
    {
        if (swayPlayer == player
                && Math.abs(lastYawOffset) > 1.0e-7)
        {
            player.setYRot(
                    (float) (
                            player.getYRot()
                                    - lastYawOffset
                    )
            );
        }

        swayPlayer = player;
        lastYawOffset = 0.0;
    }

    private static void drawEdgeVignette(
            GuiGraphicsExtractor graphics,
            int rgb,
            double opacity,
            int effectLevel
    )
    {
        int width =
                graphics.guiWidth();

        int height =
                graphics.guiHeight();

        int thickness =
                Math.max(
                        12,
                        Math.min(
                                36,
                                Math.min(
                                        width,
                                        height
                                )
                                        / (
                                                effectLevel >= 2
                                                        ? 7
                                                        : 9
                                        )
                        )
                );

        int step =
                Math.max(
                        1,
                        thickness
                                / VIGNETTE_LAYERS
                );

        for (int layer = 0;
             layer < VIGNETTE_LAYERS;
             layer++)
        {
            int offset =
                    layer * step;

            if (offset >= thickness)
            {
                break;
            }

            int band =
                    Math.min(
                            step,
                            thickness - offset
                    );

            double normalized =
                    1.0
                            - layer
                            / (double) VIGNETTE_LAYERS;

            double falloff =
                    normalized
                            * normalized;

            int alpha =
                    (int) Math.round(
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

            if (right <= left
                    || bottom <= top)
            {
                break;
            }

            graphics.fill(
                    left,
                    top,
                    right,
                    Math.min(
                            bottom,
                            top + band
                    ),
                    color
            );

            graphics.fill(
                    left,
                    Math.max(
                            top,
                            bottom - band
                    ),
                    right,
                    bottom,
                    color
            );

            int sideTop =
                    Math.min(
                            bottom,
                            top + band
                    );

            int sideBottom =
                    Math.max(
                            sideTop,
                            bottom - band
                    );

            if (sideBottom > sideTop)
            {
                graphics.fill(
                        left,
                        sideTop,
                        Math.min(
                                right,
                                left + band
                        ),
                        sideBottom,
                        color
                );

                graphics.fill(
                        Math.max(
                                left,
                                right - band
                        ),
                        sideTop,
                        right,
                        sideBottom,
                        color
                );
            }
        }
    }

    private static double smoothstep(
            double edge0,
            double edge1,
            double value
    )
    {
        double t =
                clamp01(
                        (value - edge0)
                                / (edge1 - edge0)
                );

        return t
                * t
                * (3.0 - 2.0 * t);
    }

    private static double clamp01(
            double value
    )
    {
        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        value
                )
        );
    }
}
