package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * M7 temperature HUD.
 *
 * Data acquisition, player-facing temperature conversion, and rendering remain
 * separate so later configuration can independently change units, visibility,
 * layout, and artwork without altering the canonical simulation.
 */
public final class TemperatureHudRenderer
{
    private static final Identifier BODY_GAUGE_TEXTURE =
            ColdSweatFabric.id(
                    "textures/gui/overlay/body_temp_gauge.png"
            );

    private static final int ICON_SIZE = 10;
    private static final int ICON_TEXTURE_HEIGHT = 90;

    private TemperatureHudRenderer()
    {
    }

    public static void register()
    {
        HudElementRegistry.addLast(
                ColdSweatFabric.id("temperature_hud"),
                TemperatureHudRenderer::render
        );
    }

    private static void render(
            GuiGraphicsExtractor graphics,
            net.minecraft.client.DeltaTracker deltaTracker
    )
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || player.isSpectator())
        {
            return;
        }

        TemperatureHudData data =
                TemperatureHudData.capture(player);

        int centerX = graphics.guiWidth() / 2;
        int iconY = graphics.guiHeight() - 57;

        renderNumericReadouts(
                graphics,
                minecraft,
                data,
                centerX,
                iconY
        );
    }

    /**
     * Mirrors Cold Sweat's upstream body severity semantics:
     *
     * 0..100 BODY stress spans severity 0..3.
     * 100..150 spans severity 3..7.
     *
     * The original HUD uses the 0..100 range for the regular icon transitions
     * and switches to its extreme +/-4 frame at and beyond 100.
     */
    static double getBodySeverity(double bodyStress)
    {
        double sign = Math.signum(bodyStress);
        double absolute = Math.abs(bodyStress);

        double severity;
        if (absolute < 100.0)
        {
            severity = lerp(
                    0.0,
                    3.0,
                    absolute / 100.0
            );
        }
        else
        {
            severity = lerp(
                    3.0,
                    7.0,
                    Math.min(
                            1.0,
                            (absolute - 100.0) / 50.0
                    )
            );
        }

        return severity * sign;
    }

    private static int getBodyIconStage(double bodyStress)
    {
        double severity = getBodySeverity(bodyStress);

        if (Math.abs(bodyStress) >= 100.0)
        {
            return 4 * (int) Math.signum(bodyStress);
        }

        int stage;
        if (severity >= 0.0)
        {
            stage = (int) Math.floor(severity);
        }
        else
        {
            stage = (int) Math.ceil(severity);
        }

        return Math.max(-4, Math.min(4, stage));
    }

    private static int getThreatBob(
            LocalPlayer player,
            double bodyStress
    )
    {
        int danger = Math.min(
                3,
                (int) Math.abs(getBodySeverity(bodyStress))
        );

        if (danger >= 3)
        {
            return player.tickCount % 2;
        }

        if (danger == 2 && player.tickCount % 3 == 0)
        {
            return 1;
        }

        return 0;
    }

    private static void renderNumericReadouts(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            TemperatureHudData data,
            int centerX,
            int iconY
    )
    {
        Font font = minecraft.font;

        /*
         * M7.7: centered vertical instrument with reactive text styling.
         *
         *        37.0 C
         *         [icon]
         *        24.1 C
         *
         * Body temperature lives above the icon, surroundings below.
         * Both values now react visually to temperature using the same
         * blue/orange language as Cold Sweat's body icon.
         */
        String body = String.format(
                Locale.ROOT,
                "%.1f\u00B0C",
                data.bodyCelsius()
        );

        String environment = String.format(
                Locale.ROOT,
                "%.1f\u00B0C",
                data.environmentCelsius()
        );

        int iconStage = getBodyIconStage(data.bodyStress());
        int bob = getThreatBob(
                minecraft.player,
                data.bodyStress()
        );

        int bodyIconX =
                centerX - ICON_SIZE / 2;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BODY_GAUGE_TEXTURE,
                bodyIconX,
                iconY - bob,
                0.0F,
                40.0F - iconStage * 10.0F,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                ICON_TEXTURE_HEIGHT
        );

        /*
         * Environment is the primary readout: larger and above the icon.
         * Internal body temperature is secondary: smaller and below.
         */
        drawCenteredStyledText(
                graphics,
                font,
                environment,
                centerX,
                iconY - 11,
                0.82F,
                getEnvironmentTemperatureColor(
                        data.environmentCelsius()
                ),
                getEnvironmentTemperatureAccentColor(
                        data.environmentCelsius()
                ),
                getEnvironmentTemperatureEffectLevel(
                        data.environmentCelsius()
                )
        );

        drawCenteredStyledText(
                graphics,
                font,
                body,
                centerX,
                iconY + ICON_SIZE + 3,
                0.68F,
                getBodyTemperatureColor(data.bodyCelsius()),
                getBodyTemperatureAccentColor(data.bodyCelsius()),
                getBodyTemperatureEffectLevel(data.bodyCelsius())
        );
    }

    private static void drawCenteredStyledText(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int centerX,
            int y,
            float scale,
            int baseColor,
            int accentColor,
            int effectLevel
    )
    {
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);

        float inverseScale = 1.0F / scale;

        int scaledCenterX =
                Math.round(centerX * inverseScale);

        int scaledY =
                Math.round(y * inverseScale);

        int x =
                scaledCenterX
                        - font.width(text) / 2;

        /*
         * Dangerous temperatures get a tinted accent shell so the text feels
         * more integrated with Cold Sweat's visual language than plain white
         * Minecraft text. Stage 1 adds a cardinal accent; stage 2 adds
         * diagonals for a sharper frozen/heated effect.
         */
        if (effectLevel >= 1)
        {
            graphics.text(font, text, x - 1, scaledY, accentColor, false);
            graphics.text(font, text, x + 1, scaledY, accentColor, false);
            graphics.text(font, text, x, scaledY - 1, accentColor, false);
            graphics.text(font, text, x, scaledY + 1, accentColor, false);
        }

        if (effectLevel >= 2)
        {
            graphics.text(font, text, x - 1, scaledY - 1, accentColor, false);
            graphics.text(font, text, x + 1, scaledY - 1, accentColor, false);
            graphics.text(font, text, x - 1, scaledY + 1, accentColor, false);
            graphics.text(font, text, x + 1, scaledY + 1, accentColor, false);
        }

        graphics.text(
                font,
                text,
                x,
                scaledY,
                baseColor,
                true
        );

        graphics.pose().popMatrix();
    }

    private static int getBodyTemperatureColor(double bodyCelsius)
    {
        if (bodyCelsius < 36.5)
        {
            return lerpColor(
                    0xFFFFFFFF,
                    0xFF409CFC,
                    clamp01((36.5 - bodyCelsius) / 2.0)
            );
        }

        if (bodyCelsius > 37.5)
        {
            return lerpColor(
                    0xFFFFFFFF,
                    0xFFFF803D,
                    clamp01((bodyCelsius - 37.5) / 2.0)
            );
        }

        return 0xFFFFFFFF;
    }

    private static int getBodyTemperatureAccentColor(double bodyCelsius)
    {
        if (bodyCelsius < 35.0)
        {
            return lerpColor(
                    0xFF245A8C,
                    0xFF163B63,
                    clamp01((35.0 - bodyCelsius) / 1.5)
            );
        }

        if (bodyCelsius > 39.0)
        {
            return lerpColor(
                    0xFF8F431C,
                    0xFF5F2A12,
                    clamp01((bodyCelsius - 39.0) / 1.5)
            );
        }

        return 0x00000000;
    }

    private static int getBodyTemperatureEffectLevel(double bodyCelsius)
    {
        if (bodyCelsius <= 33.5 || bodyCelsius >= 41.0)
        {
            return 2;
        }

        if (bodyCelsius <= 35.0 || bodyCelsius >= 39.5)
        {
            return 1;
        }

        return 0;
    }

    private static int getEnvironmentTemperatureColor(
            double environmentCelsius
    )
    {
        if (environmentCelsius < 18.0)
        {
            return lerpColor(
                    0xFFFFFFFF,
                    0xFF409CFC,
                    clamp01((18.0 - environmentCelsius) / 18.0)
            );
        }

        if (environmentCelsius > 26.0)
        {
            return lerpColor(
                    0xFFFFFFFF,
                    0xFFFF803D,
                    clamp01((environmentCelsius - 26.0) / 18.0)
            );
        }

        return 0xFFE8E8E8;
    }

    private static int getEnvironmentTemperatureAccentColor(
            double environmentCelsius
    )
    {
        if (environmentCelsius < -5.0)
        {
            return lerpColor(
                    0xFF245A8C,
                    0xFF163B63,
                    clamp01((-5.0 - environmentCelsius) / 15.0)
            );
        }

        if (environmentCelsius > 42.0)
        {
            return lerpColor(
                    0xFF8F431C,
                    0xFF5F2A12,
                    clamp01((environmentCelsius - 42.0) / 14.0)
            );
        }

        return 0x00000000;
    }

    private static int getEnvironmentTemperatureEffectLevel(
            double environmentCelsius
    )
    {
        if (environmentCelsius <= -18.0 || environmentCelsius >= 52.0)
        {
            return 2;
        }

        if (environmentCelsius <= -5.0 || environmentCelsius >= 42.0)
        {
            return 1;
        }

        return 0;
    }

    private static int lerpColor(
            int startColor,
            int endColor,
            double delta
    )
    {
        double t = clamp01(delta);

        int startA = (startColor >> 24) & 0xFF;
        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8) & 0xFF;
        int startB = startColor & 0xFF;

        int endA = (endColor >> 24) & 0xFF;
        int endR = (endColor >> 16) & 0xFF;
        int endG = (endColor >> 8) & 0xFF;
        int endB = endColor & 0xFF;

        int a = (int) Math.round(lerp(startA, endA, t));
        int r = (int) Math.round(lerp(startR, endR, t));
        int g = (int) Math.round(lerp(startG, endG, t));
        int b = (int) Math.round(lerp(startB, endB, t));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static double clamp01(double value)
    {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double lerp(
            double start,
            double end,
            double delta
    )
    {
        return start + (end - start)
                * Math.max(0.0, Math.min(1.0, delta));
    }
}
