package com.momosoftworks.coldsweat.fabric.client;

import com.momosoftworks.coldsweat.api.util.Hydration;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

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

    /* M8.3 contaminated-water / Thirst presentation. */
    private static final int THIRST_OUTLINE = 0xFF33421D;
    private static final int THIRST_EMPTY_INTERIOR = 0xFF4B5C2A;
    private static final int THIRST_WATER = 0xFF78963B;
    private static final int THIRST_WATER_LIGHT = 0xFFA2B958;
    private static final int THIRST_WATER_SHADOW = 0xFF566F2C;
    private static final int THIRST_SATURATION = 0xFFD6E58B;

    /*
     * Client-only HUD feedback state. Tick deadlines keep the animation
     * deterministic and frame-rate independent.
     */
    private static UUID lastPlayerId;
    private static double lastHydration = Double.NaN;
    private static boolean lastThirst;
    private static int shakeUntilTick;
    private static int thirstPulseUntilTick;

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

        boolean thirst =
                player.hasEffect(ModEffects.THIRST);

        updateAnimationState(
                player,
                hydration,
                thirst
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

            int jitterX =
                    getJitterX(
                            player.tickCount,
                            slot,
                            thirst
                    );

            int jitterY =
                    getJitterY(
                            player.tickCount,
                            slot,
                            thirst
                    );

            drawDroplet(
                    graphics,
                    x + jitterX,
                    y + jitterY,
                    hydrationInSlot,
                    saturationInSlot,
                    thirst
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
            double saturationPoints,
            boolean thirst
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
                    color =
                            thirst
                                    ? THIRST_OUTLINE
                                    : OUTLINE;
                }
                else if (isFilledForStage(px, hydrationStage))
                {
                    color =
                            getWaterColor(
                                    px,
                                    py,
                                    thirst
                            );
                }
                else
                {
                    color =
                            thirst
                                    ? THIRST_EMPTY_INTERIOR
                                    : EMPTY_INTERIOR;
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
                    hydrationStage,
                    thirst
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
             * Hydration drains from the bar's left edge toward the right.
             * Therefore a half-consumed droplet keeps its RIGHT half and loses
             * its LEFT half first.
             */
            return px >= 4;
        }

        return false;
    }

    private static int getWaterColor(
            int px,
            int py,
            boolean thirst
    )
    {
        if (px <= 3 && py <= 4)
        {
            return thirst
                    ? THIRST_WATER_LIGHT
                    : WATER_LIGHT;
        }

        if (px >= 5 || py >= 7)
        {
            return thirst
                    ? THIRST_WATER_SHADOW
                    : WATER_SHADOW;
        }

        return thirst
                ? THIRST_WATER
                : WATER;
    }

    private static void drawSaturationSheen(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int saturationStage,
            int hydrationStage,
            boolean thirst
    )
    {
        /*
         * Half saturation follows the same direction as hydration: only the
         * surviving RIGHT half receives a sheen. Full saturation then adds
         * the left-side highlights.
         */
        drawSheenPixel(
                graphics,
                x,
                y,
                5,
                5,
                saturationStage,
                hydrationStage,
                thirst
        );

        drawSheenPixel(
                graphics,
                x,
                y,
                4,
                6,
                saturationStage,
                hydrationStage,
                thirst
        );

        if (saturationStage >= 2)
        {
            drawSheenPixel(
                    graphics,
                    x,
                    y,
                    3,
                    3,
                    saturationStage,
                    hydrationStage,
                    thirst
            );

            drawSheenPixel(
                    graphics,
                    x,
                    y,
                    2,
                    5,
                    saturationStage,
                    hydrationStage,
                    thirst
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
            int hydrationStage,
            boolean thirst
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
                thirst
                        ? THIRST_SATURATION
                        : SATURATION
        );
    }

    private static void updateAnimationState(
            LocalPlayer player,
            double hydration,
            boolean thirst
    )
    {
        UUID playerId =
                player.getUUID();

        if (!playerId.equals(lastPlayerId))
        {
            lastPlayerId = playerId;
            lastHydration = hydration;
            lastThirst = thirst;
            shakeUntilTick = 0;
            thirstPulseUntilTick = 0;
            return;
        }

        int tick =
                player.tickCount;

        if (!Double.isNaN(lastHydration)
                && hydration < lastHydration - 1.0e-6)
        {
            /*
             * Short hunger-like shudder whenever hydration actually drops.
             */
            shakeUntilTick =
                    Math.max(
                            shakeUntilTick,
                            tick + 8
                    );
        }

        if (thirst && !lastThirst)
        {
            /*
             * Applying Thirst deserves a more visible initial shudder, then
             * settles into the subtler sickly-state jitter below.
             */
            thirstPulseUntilTick =
                    Math.max(
                            thirstPulseUntilTick,
                            tick + 14
                    );
        }

        lastHydration = hydration;
        lastThirst = thirst;
    }

    private static int getJitterX(
            int tick,
            int slot,
            boolean thirst
    )
    {
        if (tick < thirstPulseUntilTick
                && (tick + slot) % 4 == 0)
        {
            return (slot & 1) == 0
                    ? -1
                    : 1;
        }

        return 0;
    }

    private static int getJitterY(
            int tick,
            int slot,
            boolean thirst
    )
    {
        if (tick < thirstPulseUntilTick)
        {
            int phase =
                    (tick + slot * 2) % 3;

            return phase - 1;
        }

        if (tick < shakeUntilTick)
        {
            return (tick + slot) % 2 == 0
                    ? 1
                    : 0;
        }

        /*
         * While Thirst remains active, keep a sparse one-pixel twitch rather
         * than continuously vibrating the entire HUD.
         */
        if (thirst
                && (tick + slot * 3) % 7 == 0)
        {
            return 1;
        }

        return 0;
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
