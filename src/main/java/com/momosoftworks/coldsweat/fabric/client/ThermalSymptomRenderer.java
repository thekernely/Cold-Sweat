package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * Stronger Cold Sweat presentation pass.
 *
 * Simulation remains authoritative elsewhere. This renderer only turns
 * synchronized thermal state into clearer visual feedback.
 */
public final class ThermalSymptomRenderer
{
    private static final int COLD_RGB = 0x6EAFFF;
    private static final int HEAT_RGB = 0xFF7B42;
    private static final int VIGNETTE_LAYERS = 10;

    private static LocalPlayer shiverPlayer;
    private static double lastShiverYawOffset;

    private ThermalSymptomRenderer() {}

    public static void register()
    {
        HudElementRegistry.addLast(
                ColdSweatFabric.id("thermal_symptom_vignette"),
                ThermalSymptomRenderer::render
        );
    }

    private static void render(GuiGraphicsExtractor graphics,
                               net.minecraft.client.DeltaTracker deltaTracker)
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null)
        {
            shiverPlayer = null;
            lastShiverYawOffset = 0.0;
            return;
        }

        if (player.isSpectator() || player.isCreative() || minecraft.isPaused())
        {
            clearShiver(player);
            return;
        }

        TemperatureHudData data = TemperatureHudData.capture(player);
        double coreCelsius = data.bodyCelsius();
        double environmentCelsius = data.environmentCelsius();

        applyShiver(player, coreCelsius);

        double coldCore = coldOpacity(coreCelsius);
        double heatCore = heatOpacity(coreCelsius);

        // Immediate environment warning stays capped below physiological severity.
        double coldExposure = 0.12 * clamp01((-8.0 - environmentCelsius) / 22.0);
        double heatExposure = 0.12 * clamp01((environmentCelsius - 38.0) / 24.0);

        boolean cold = Math.max(coldCore, coldExposure) >= Math.max(heatCore, heatExposure);
        double opacity = cold
                ? Math.max(coldCore, coldExposure)
                : Math.max(heatCore, heatExposure);

        if (opacity <= 0.0) return;

        double severeCoreProgress = cold
                ? clamp01((33.8 - coreCelsius) / 0.8)
                : clamp01((coreCelsius - 40.7) / 1.1);

        double severeEnvironmentProgress = cold
                ? clamp01((-18.0 - environmentCelsius) / 18.0)
                : clamp01((environmentCelsius - 52.0) / 20.0);

        double severeProgress = Math.max(severeCoreProgress, severeEnvironmentProgress * 0.55);

        double pulseDepth = 0.10 * severeProgress;
        double pulse = 1.0 - pulseDepth
                + pulseDepth * Math.sin(player.tickCount * 0.22);

        drawEdgeVignette(
                graphics,
                cold ? COLD_RGB : HEAT_RGB,
                opacity * pulse,
                severeProgress > 0.0 ? 2 : 1
        );

        if (severeProgress > 0.08)
        {
            drawSevereAccents(graphics, cold, severeProgress * pulse);
        }
    }

    private static void applyShiver(LocalPlayer player, double coreCelsius)
    {
        double severity = clamp01((35.0 - coreCelsius) / 2.0);

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

        double seconds = System.nanoTime() / 1_000_000_000.0;
        double perceptualSeverity = Math.sqrt(severity);

        double envelopeFrequency = lerp(0.48, 0.78, perceptualSeverity);
        double envelopeWave = 0.5
                + 0.5 * Math.sin(seconds * Math.PI * 2.0 * envelopeFrequency);

        double dutyThreshold = lerp(0.72, 0.06, perceptualSeverity);
        double envelope = smoothstep(dutyThreshold, 1.0, envelopeWave);

        double amplitudeDegrees = lerp(0.045, 0.30, perceptualSeverity);
        double shiverFrequency = lerp(7.0, 11.5, perceptualSeverity);

        double targetOffset = Math.sin(seconds * Math.PI * 2.0 * shiverFrequency)
                * amplitudeDegrees * envelope;

        double delta = targetOffset - lastShiverYawOffset;

        if (Math.abs(delta) > 1.0e-7)
        {
            player.setYRot((float) (player.getYRot() + delta));
        }

        lastShiverYawOffset = targetOffset;
    }

    private static void clearShiver(LocalPlayer player)
    {
        if (shiverPlayer == player && Math.abs(lastShiverYawOffset) > 1.0e-7)
        {
            player.setYRot((float) (player.getYRot() - lastShiverYawOffset));
        }

        shiverPlayer = player;
        lastShiverYawOffset = 0.0;
    }

    private static double smoothstep(double edge0, double edge1, double value)
    {
        if (edge1 <= edge0) return value >= edge1 ? 1.0 : 0.0;

        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double coldOpacity(double coreCelsius)
    {
        if (coreCelsius > 35.0) return 0.0;

        if (coreCelsius >= 34.0)
        {
            return lerp(0.08, 0.20, (35.0 - coreCelsius));
        }

        if (coreCelsius >= 33.5)
        {
            return lerp(0.20, 0.34, (34.0 - coreCelsius) / 0.5);
        }

        return lerp(0.34, 0.55, clamp01((33.5 - coreCelsius) / 0.7));
    }

    private static double heatOpacity(double coreCelsius)
    {
        if (coreCelsius < 39.5) return 0.0;

        if (coreCelsius <= 40.5)
        {
            return lerp(0.08, 0.20, (coreCelsius - 39.5));
        }

        if (coreCelsius <= 41.0)
        {
            return lerp(0.20, 0.34, (coreCelsius - 40.5) / 0.5);
        }

        return lerp(0.34, 0.52, clamp01((coreCelsius - 41.0) / 1.0));
    }

    private static void drawEdgeVignette(GuiGraphicsExtractor graphics,
                                         int rgb,
                                         double opacity,
                                         int effectLevel)
    {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        int thickness = Math.max(
                18,
                Math.min(
                        effectLevel >= 2 ? 64 : 46,
                        Math.min(width, height) / (effectLevel >= 2 ? 4 : 7)
                )
        );

        int step = Math.max(1, thickness / VIGNETTE_LAYERS);

        for (int layer = 0; layer < VIGNETTE_LAYERS; layer++)
        {
            int offset = layer * step;
            if (offset >= thickness) break;

            int band = Math.min(step, thickness - offset);
            double normalized = 1.0 - layer / (double) VIGNETTE_LAYERS;
            double falloff = normalized * normalized;
            int alpha = (int) Math.round(255.0 * opacity * falloff);

            if (alpha <= 0) continue;

            int color = (alpha << 24) | (rgb & 0x00FFFFFF);

            int left = offset;
            int top = offset;
            int right = width - offset;
            int bottom = height - offset;

            if (right <= left || bottom <= top) break;

            graphics.fill(left, top, right, Math.min(bottom, top + band), color);
            graphics.fill(left, Math.max(top, bottom - band), right, bottom, color);

            int sideTop = Math.min(bottom, top + band);
            int sideBottom = Math.max(sideTop, bottom - band);

            if (sideBottom > sideTop)
            {
                graphics.fill(left, sideTop, Math.min(right, left + band), sideBottom, color);
                graphics.fill(Math.max(left, right - band), sideTop, right, sideBottom, color);
            }
        }
    }

    private static void drawSevereAccents(GuiGraphicsExtractor graphics,
                                          boolean cold,
                                          double severity)
    {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        if (cold)
        {
            int alpha = (int) Math.round(120.0 * clamp01(severity));
            int color = (alpha << 24) | 0x00CFEAFF;

            for (int i = 0; i < 7; i++)
            {
                int inset = i * 5;
                int span = 16 + i * 5;

                graphics.fill(0, inset, span, inset + 3, color);
                graphics.fill(width - span, inset, width, inset + 3, color);
                graphics.fill(0, height - inset - 3, span, height - inset, color);
                graphics.fill(width - span, height - inset - 3, width, height - inset, color);
            }
        }
        else
        {
            int washAlpha = (int) Math.round(26.0 * clamp01(severity));
            int wash = (washAlpha << 24) | 0x00FF5E2E;
            graphics.fill(0, 0, width, height, wash);

            int edgeAlpha = (int) Math.round(82.0 * clamp01(severity));
            int edge = (edgeAlpha << 24) | 0x00FF8A45;
            int band = Math.max(2, Math.min(width, height) / 90);

            graphics.fill(0, 0, width, band, edge);
            graphics.fill(0, height - band, width, height, edge);
        }
    }

    private static double lerp(double start, double end, double delta)
    {
        return start + (end - start) * clamp01(delta);
    }

    private static double clamp01(double value)
    {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
