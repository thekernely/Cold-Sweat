package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.Locale;

/**
 * M7.1 observability HUD.
 *
 * This is intentionally data-first rather than final art. The renderer only
 * consumes TemperatureHudData, so later gauge textures, positioning settings,
 * unit settings, and compact/detailed layouts do not need to touch the
 * synchronized temperature runtime.
 */
public final class TemperatureHudRenderer
{
    private static final int BAR_WIDTH = 82;
    private static final int BAR_HEIGHT = 5;

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

        if (player == null
                || player.isSpectator())
        {
            return;
        }

        TemperatureHudData data =
                TemperatureHudData.capture(player);

        int centerX = graphics.guiWidth() / 2;
        int baseY = graphics.guiHeight() - 64;

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

        Font font = minecraft.font;
        int bodyWidth = font.width(body);
        int environmentWidth = font.width(environment);
        int textWidth = bodyWidth + 12 + environmentWidth;

        int panelLeft = centerX - Math.max(textWidth + 10, BAR_WIDTH + 8) / 2;
        int panelRight = centerX + Math.max(textWidth + 10, BAR_WIDTH + 8) / 2;

        graphics.fill(
                panelLeft,
                baseY - 13,
                panelRight,
                baseY + 12,
                0x90000000
        );

        int textX = centerX - textWidth / 2;
        graphics.text(
                font,
                body,
                textX,
                baseY - 10,
                0xFFFFFFFF,
                true
        );
        graphics.text(
                font,
                environment,
                textX + bodyWidth + 12,
                baseY - 10,
                0xFFFFFFFF,
                true
        );

        int barX = centerX - BAR_WIDTH / 2;
        int barY = baseY + 2;
        int half = BAR_WIDTH / 2;

        graphics.fill(
                barX,
                barY,
                barX + half,
                barY + BAR_HEIGHT,
                0xFF4386E6
        );
        graphics.fill(
                barX + half,
                barY,
                barX + BAR_WIDTH,
                barY + BAR_HEIGHT,
                0xFFF08A24
        );

        int neutralX = barX + half;
        graphics.fill(
                neutralX,
                barY - 1,
                neutralX + 1,
                barY + BAR_HEIGHT + 1,
                0xFFB8B8B8
        );

        double clampedStress = Math.max(
                -100.0,
                Math.min(100.0, data.bodyStress())
        );
        int markerX = barX + (int) Math.round(
                (clampedStress + 100.0)
                        / 200.0
                        * (BAR_WIDTH - 1)
        );

        graphics.fill(
                markerX - 1,
                barY - 2,
                markerX + 2,
                barY + BAR_HEIGHT + 2,
                0xFFFFFFFF
        );
    }
}
