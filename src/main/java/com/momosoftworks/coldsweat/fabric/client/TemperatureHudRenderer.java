package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
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
    private static final int MARKER_SIZE = 7;
    private static final int HUD_TEXTURE_WIDTH = 26;
    private static final int ICON_TEXTURE_HEIGHT = 90;
    private static final int ENVIRONMENT_MARKER_U = 11;
    private static final int BODY_MARKER_U = 19;

    /*
     * Visual symmetry is anchored to the center face, not to equal-width
     * outer boxes.  Both temperature strings keep the same visible gap from
     * the face; their marker then hugs the outside edge of the string.
     */
    private static final int READOUT_TO_FACE_GAP = 3;
    private static final int MARKER_GAP = 2;
    private static final float READOUT_TEXT_SCALE = 0.54F;

    private TemperatureHudRenderer()
    {
    }

    public static void register()
    {
        /*
         * M7.14d: shift vanilla's held-item tooltip through Fabric's own HUD
         * registry instead of mixing into Minecraft's private Hud methods.
         *
         * Minecraft/Fabric still own the actual tooltip element, including
         * visibility, timer, text, rarity formatting, fade and centering.
         * We only wrap its extraction in a temporary 12px upward transform.
         */
        HudElementRegistry.replaceElement(
                VanillaHudElements.HELD_ITEM_TOOLTIP,
                original -> (graphics, deltaTracker) ->
                {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(0.0F, -12.0F);

                    original.extractRenderState(
                            graphics,
                            deltaTracker
                    );

                    graphics.pose().popMatrix();
                }
        );

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

        if (player == null || player.isSpectator() || player.isCreative())
        {
            return;
        }

        TemperatureHudData data =
                TemperatureHudData.capture(player);

        int centerX = graphics.guiWidth() / 2;

        /*
         * M7.14d: reserve one stable row above vanilla armor/health.
         * The temperature HUD stays fixed; the transient held-item tooltip is
         * shifted upward independently through Fabric's HUD registry.
         */
        int iconY = graphics.guiHeight() - 59;

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
         * M7.13k: compact horizontal instrument with optical edge anchoring.
         *
         *      [sun] 24°  [face]  37.0° [heart]
         *
         * The temperature values themselves are anchored to equal gaps from
         * the center face.  That is what the eye reads as symmetry; marker
         * positions then follow the outside edges of their values.
         */
        String body = String.format(
                Locale.ROOT,
                "%.1f\u00B0",
                data.bodyCelsius()
        );

        /*
         * Environment is intentionally coarse on the HUD.  Whole degrees are
         * easier to scan and avoid implying physiological-style precision for
         * an external apparent-temperature estimate.
         */
        String environment = String.format(
                Locale.ROOT,
                "%.0f\u00B0",
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
                HUD_TEXTURE_WIDTH,
                ICON_TEXTURE_HEIGHT
        );

        /*
         * Anchor the center-facing edges of both values to the face:
         *
         *   marker <- value | gap | face | gap | value -> marker
         *
         * This is optically symmetric even though "29°" and "37.0°" have
         * different widths.
         */
        int faceLeft = centerX - ICON_SIZE / 2;
        int faceRight = faceLeft + ICON_SIZE;

        int environmentTextRight =
                faceLeft - READOUT_TO_FACE_GAP;
        int bodyTextLeft =
                faceRight + READOUT_TO_FACE_GAP;

        int environmentRenderedWidth =
                Math.round(font.width(environment) * READOUT_TEXT_SCALE);
        int bodyRenderedWidth =
                Math.round(font.width(body) * READOUT_TEXT_SCALE);

        int environmentMarkerX =
                environmentTextRight
                        - environmentRenderedWidth
                        - MARKER_GAP
                        - MARKER_SIZE;

        int bodyMarkerX =
                bodyTextLeft
                        + bodyRenderedWidth
                        + MARKER_GAP;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BODY_GAUGE_TEXTURE,
                environmentMarkerX,
                iconY + 1,
                ENVIRONMENT_MARKER_U,
                0.0F,
                MARKER_SIZE,
                MARKER_SIZE,
                HUD_TEXTURE_WIDTH,
                ICON_TEXTURE_HEIGHT
        );

        drawRightAlignedStyledText(
                graphics,
                font,
                environment,
                environmentTextRight,
                iconY,
                READOUT_TEXT_SCALE,
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

        drawLeftAlignedStyledText(
                graphics,
                font,
                body,
                bodyTextLeft,
                iconY,
                READOUT_TEXT_SCALE,
                getBodyTemperatureColor(data.bodyCelsius()),
                getBodyTemperatureAccentColor(data.bodyCelsius()),
                getBodyTemperatureEffectLevel(data.bodyCelsius())
        );

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BODY_GAUGE_TEXTURE,
                bodyMarkerX,
                iconY + 1,
                BODY_MARKER_U,
                0.0F,
                MARKER_SIZE,
                MARKER_SIZE,
                HUD_TEXTURE_WIDTH,
                ICON_TEXTURE_HEIGHT
        );
    }

    private static void drawRightAlignedStyledText(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int rightX,
            int y,
            float scale,
            int baseColor,
            int accentColor,
            int effectLevel
    )
    {
        drawAlignedStyledText(
                graphics,
                font,
                text,
                rightX,
                y,
                scale,
                baseColor,
                accentColor,
                effectLevel,
                true
        );
    }

    private static void drawLeftAlignedStyledText(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int leftX,
            int y,
            float scale,
            int baseColor,
            int accentColor,
            int effectLevel
    )
    {
        drawAlignedStyledText(
                graphics,
                font,
                text,
                leftX,
                y,
                scale,
                baseColor,
                accentColor,
                effectLevel,
                false
        );
    }

    private static void drawAlignedStyledText(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int anchorX,
            int y,
            float scale,
            int baseColor,
            int accentColor,
            int effectLevel,
            boolean rightAligned
    )
    {
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);

        float inverseScale = 1.0F / scale;

        int scaledAnchorX =
                Math.round(anchorX * inverseScale);

        int scaledY =
                Math.round(y * inverseScale);

        int x =
                rightAligned
                        ? scaledAnchorX - font.width(text)
                        : scaledAnchorX;

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

