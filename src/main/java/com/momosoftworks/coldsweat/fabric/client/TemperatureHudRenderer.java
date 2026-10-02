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
        int iconY = graphics.guiHeight() - 49;

        int iconStage = getBodyIconStage(data.bodyStress());
        int bob = getThreatBob(player, data.bodyStress());

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BODY_GAUGE_TEXTURE,
                centerX - ICON_SIZE / 2,
                iconY - bob,
                0.0F,
                40.0F - iconStage * 10.0F,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                ICON_TEXTURE_HEIGHT
        );

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

        String body = String.format(
                Locale.ROOT,
                "Body %.1f\u00B0C",
                data.bodyCelsius()
        );
        String environment = String.format(
                Locale.ROOT,
                "Surroundings %.1f\u00B0C",
                data.environmentCelsius()
        );

        int gap = 12;
        int bodyWidth = font.width(body);

        int bodyX = centerX - gap - bodyWidth;
        int environmentX = centerX + gap;
        int textY = iconY + 1;

        int bodyColor = getBodyTextColor(data.bodyStress());

        graphics.text(
                font,
                body,
                bodyX,
                textY,
                bodyColor,
                true
        );

        graphics.text(
                font,
                environment,
                environmentX,
                textY,
                0xFFFFFFFF,
                true
        );
    }

    private static int getBodyTextColor(double bodyStress)
    {
        if (bodyStress > 0.0)
        {
            return 0xFFFF803D;
        }

        if (bodyStress < 0.0)
        {
            return 0xFF409CFC;
        }

        return 0xFFFFFFFF;
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
