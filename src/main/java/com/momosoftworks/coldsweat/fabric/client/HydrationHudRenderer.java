package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.api.util.Hydration;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * M8.2 hydration HUD.
 *
 * Ten droplets represent the canonical 0..20 hydration range. Hydration
 * saturation is visible as a pale inner highlight on the same droplets rather
 * than consuming another HUD row.
 *
 * The element is registered as a right-side status bar immediately above
 * vanilla FOOD_BAR. Registering a height provider means vanilla AIR_BAR and
 * later right-side status bars automatically move upward when this bar exists.
 */
public final class HydrationHudRenderer
{
    private static final Identifier HUD_ID =
            ColdSweatFabric.id("hydration_hud");

    private static final int ICON_COUNT = 10;
    private static final int ICON_WIDTH = 9;
    private static final int ICON_HEIGHT = 9;
    private static final int ICON_STEP = 8;
    private static final int STATUS_BAR_HEIGHT = 10;
    private static final int AIR_BAR_CENTER_SHIFT = 48;

    /*
     * Deliberately restrained Minecraft-style palette. The empty droplet keeps
     * enough contrast to remain readable against light terrain without looking
     * brighter than vanilla health/hunger.
     */
    private static final int OUTLINE = 0xFF18344A;
    private static final int EMPTY_INTERIOR = 0xFF25475E;
    private static final int WATER = 0xFF3895D8;
    private static final int WATER_LIGHT = 0xFF60BCE8;
    private static final int WATER_SHADOW = 0xFF256AAE;
    private static final int SATURATION = 0xFFB6F3FF;

    /*
     * 9x9 tear-drop silhouette. '#' is part of the icon; '.' is transparent.
     * Rendering is procedural so M8.2 adds no new texture/resource plumbing.
     */
    private static final String[] SHAPE =
            {
                    "....#....",
                    "...###...",
                    "...###...",
                    "..#####..",
                    "..#####..",
                    ".#######.",
                    ".#######.",
                    "..#####..",
                    "...###..."
            };

    private HydrationHudRenderer()
    {
    }

    public static void register()
    {
        /*
         * FOOD_BAR remains the bottom-right survival resource row.
         * Hydration is attached directly after it, so it occupies the next row.
         * AIR_BAR is later in the vanilla stack and therefore receives the
         * custom height offset automatically.
         */
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.FOOD_BAR,
                HUD_ID,
                HydrationHudRenderer::render
        );

        HudStatusBarHeightRegistry.addRight(
                HUD_ID,
                player -> shouldRender(player)
                        ? STATUS_BAR_HEIGHT
                        : 0
        );

        /*
         * Hydration correctly pushes vanilla AIR_BAR above Hunger, but our
         * fixed center temperature instrument occupies that same vertical
         * band. Air is transient, so move only the vanilla air element one
         * row upward and 48px left, optically centering its 10-bubble strip
         * over the temperature instrument. Vanilla still owns its bubbles,
         * timing, visibility and depletion animation.
         */
        HudElementRegistry.replaceElement(
                VanillaHudElements.AIR_BAR,
                original -> (graphics, deltaTracker) ->
                {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(
                            -AIR_BAR_CENTER_SHIFT,
                            -STATUS_BAR_HEIGHT
                    );

                    original.extractRenderState(
                            graphics,
                            deltaTracker
                    );

                    graphics.pose().popMatrix();
                }
        );
    }

    private static void render(
            GuiGraphicsExtractor graphics,
            net.minecraft.client.DeltaTracker deltaTracker
    )
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (!shouldRender(player))
        {
            return;
        }

        double hydration =
                clamp(
                        Hydration.get(player),
                        0.0,
                        Hydration.MAX_HYDRATION
                );

        double saturation =
                clamp(
                        Hydration.getSaturation(player),
                        0.0,
                        hydration
                );

        int centerX = graphics.guiWidth() / 2;

        /*
         * Match vanilla's 10-icon right-side bar geometry:
         * rightmost icon starts at center + 82 and subsequent icons step left
         * by 8 px, producing the same slight icon overlap as Hunger.
         */
        int rightmostX = centerX + 82;

        /*
         * Ask Fabric for the calculated status-bar height rather than hard-code
         * a screen Y. This keeps hydration compatible with vanilla food, air,
         * mount-health and other mods using the same status-bar registry.
         */
        int y =
                graphics.guiHeight()
                        - HudStatusBarHeightRegistry.getHeight(HUD_ID);

        for (int slot = 0; slot < ICON_COUNT; slot++)
        {
            int x =
                    rightmostX
                            - slot * ICON_STEP;

            double hydrationInSlot =
                    clamp(
                            hydration - slot * 2.0,
                            0.0,
                            2.0
                    );

            double saturationInSlot =
                    clamp(
                            saturation - slot * 2.0,
                            0.0,
                            2.0
                    );

            drawDroplet(
                    graphics,
                    x,
                    y,
                    hydrationInSlot,
                    saturationInSlot
            );
        }
    }

    private static boolean shouldRender(Player player)
    {
        return player != null
                && !player.isCreative()
                && !player.isSpectator();
    }

    private static void drawDroplet(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            double hydrationPoints,
            double saturationPoints
    )
    {
        int hydrationStage =
                pointsToStage(hydrationPoints);

        int saturationStage =
                pointsToStage(saturationPoints);

        for (int py = 0; py < ICON_HEIGHT; py++)
        {
            for (int px = 0; px < ICON_WIDTH; px++)
            {
                if (!isShapePixel(px, py))
                {
                    continue;
                }

                boolean outline =
                        isOutlinePixel(px, py);

                int color;
                if (outline)
                {
                    color = OUTLINE;
                }
                else if (isFilledForStage(px, hydrationStage))
                {
                    color =
                            getWaterColor(px, py);
                }
                else
                {
                    color = EMPTY_INTERIOR;
                }

                drawPixel(
                        graphics,
                        x + px,
                        y + py,
                        color
                );
            }
        }

        /*
         * Saturation is a secondary reserve, so its visualization should read
         * as a sheen on existing water rather than a second fill meter.
         */
        if (saturationStage > 0)
        {
            drawSaturationSheen(
                    graphics,
                    x,
                    y,
                    saturationStage,
                    hydrationStage
            );
        }
    }

    private static int pointsToStage(double points)
    {
        if (points >= 1.5)
        {
            return 2;
        }

        return points > 0.0
                ? 1
                : 0;
    }

    private static boolean isFilledForStage(
            int px,
            int stage
    )
    {
        if (stage >= 2)
        {
            return true;
        }

        if (stage == 1)
        {
            /*
             * Like vanilla half-food icons, the half state occupies one side
             * of the icon rather than becoming a vertically cropped puddle.
             */
            return px <= 4;
        }

        return false;
    }

    private static int getWaterColor(
            int px,
            int py
    )
    {
        if (px <= 3 && py <= 4)
        {
            return WATER_LIGHT;
        }

        if (px >= 5 || py >= 7)
        {
            return WATER_SHADOW;
        }

        return WATER;
    }

    private static void drawSaturationSheen(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int saturationStage,
            int hydrationStage
    )
    {
        /*
         * These pixels sit inside the droplet silhouette. Half saturation uses
         * only the left glint; full saturation adds the right/bottom glints.
         * Never draw sheen where the corresponding hydration half is empty.
         */
        drawSheenPixel(
                graphics,
                x,
                y,
                3,
                3,
                saturationStage,
                hydrationStage
        );

        drawSheenPixel(
                graphics,
                x,
                y,
                2,
                5,
                saturationStage,
                hydrationStage
        );

        if (saturationStage >= 2)
        {
            drawSheenPixel(
                    graphics,
                    x,
                    y,
                    5,
                    5,
                    saturationStage,
                    hydrationStage
            );

            drawSheenPixel(
                    graphics,
                    x,
                    y,
                    4,
                    6,
                    saturationStage,
                    hydrationStage
            );
        }
    }

    private static void drawSheenPixel(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int px,
            int py,
            int saturationStage,
            int hydrationStage
    )
    {
        if (saturationStage <= 0
                || !isShapePixel(px, py)
                || isOutlinePixel(px, py)
                || !isFilledForStage(px, hydrationStage))
        {
            return;
        }

        drawPixel(
                graphics,
                x + px,
                y + py,
                SATURATION
        );
    }

    private static boolean isShapePixel(
            int x,
            int y
    )
    {
        return y >= 0
                && y < SHAPE.length
                && x >= 0
                && x < SHAPE[y].length()
                && SHAPE[y].charAt(x) == '#';
    }

    private static boolean isOutlinePixel(
            int x,
            int y
    )
    {
        return !isShapePixel(x - 1, y)
                || !isShapePixel(x + 1, y)
                || !isShapePixel(x, y - 1)
                || !isShapePixel(x, y + 1);
    }

    private static void drawPixel(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int color
    )
    {
        graphics.fill(
                x,
                y,
                x + 1,
                y + 1,
                color
        );
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum
    )
    {
        return Math.max(
                minimum,
                Math.min(maximum, value)
        );
    }
}
