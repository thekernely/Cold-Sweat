package com.momosoftworks.coldsweat.fabric;

import com.momosoftworks.coldsweat.client.renderer.block.HearthBlockEntityRenderer;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.common.container.BoilerMenu;
import com.momosoftworks.coldsweat.common.container.HearthMenu;
import com.momosoftworks.coldsweat.common.container.IceboxMenu;
import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import com.momosoftworks.coldsweat.core.init.ModFluids;
import com.momosoftworks.coldsweat.core.init.ModMenus;
import com.momosoftworks.coldsweat.fabric.client.DehydrationSymptomRenderer;
import com.momosoftworks.coldsweat.fabric.client.HeatExposureParticleRuntime;
import com.momosoftworks.coldsweat.fabric.client.HydrationHudRenderer;
import com.momosoftworks.coldsweat.fabric.client.TemperatureHudRenderer;
import com.momosoftworks.coldsweat.fabric.client.ThermalSymptomRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Locale;

public final class ColdSweatFabricClient implements ClientModInitializer
{
    private static final Identifier HEARTH_GUI =
            ColdSweatFabric.id("textures/gui/screen/hearth_gui.png");
    private static final Identifier BOILER_GUI =
            ColdSweatFabric.id("textures/gui/screen/boiler_gui.png");
    private static final Identifier ICEBOX_GUI =
            ColdSweatFabric.id("textures/gui/screen/icebox_gui.png");

    private static final Identifier HOT_GAUGE =
            ColdSweatFabric.id("textures/gui/sprites/hearth/fuel_gauge_hot.png");
    private static final Identifier HOT_GAUGE_EMPTY =
            ColdSweatFabric.id("textures/gui/sprites/hearth/fuel_gauge_hot_empty.png");
    private static final Identifier COLD_GAUGE =
            ColdSweatFabric.id("textures/gui/sprites/hearth/fuel_gauge_cold.png");
    private static final Identifier COLD_GAUGE_EMPTY =
            ColdSweatFabric.id("textures/gui/sprites/hearth/fuel_gauge_cold_empty.png");

    @Override
    public void onInitializeClient()
    {
        ModelLayerRegistry.registerModelLayer(
                HearthBlockEntityRenderer.LAYER_LOCATION,
                HearthBlockEntityRenderer::createBodyLayer
        );

        BlockEntityRenderers.register(
                ModBlockEntities.HEARTH,
                HearthBlockEntityRenderer::new
        );

        MenuScreens.register(ModMenus.HEARTH, HearthScreen::new);
        MenuScreens.register(ModMenus.BOILER, BoilerScreen::new);
        MenuScreens.register(ModMenus.ICEBOX, IceboxScreen::new);

        HeatExposureParticleRuntime.register();
        ThermalSymptomRenderer.register();
        DehydrationSymptomRenderer.register();
        HydrationHudRenderer.register();
        TemperatureHudRenderer.register();

        FluidModel.Unbaked slushModel =
                new FluidModel.Unbaked(
                        new Material(
                                Identifier.fromNamespaceAndPath(
                                        "minecraft",
                                        "block/water_still"
                                )
                        ),
                        new Material(
                                Identifier.fromNamespaceAndPath(
                                        "minecraft",
                                        "block/water_flow"
                                )
                        ),
                        null,
                        state -> 0xA9D9FF
                );

        FluidRenderingRegistry.register(
                ModFluids.SLUSH,
                ModFluids.FLOWING_SLUSH,
                slushModel
        );
    }

    private abstract static class ThermalMachineScreen<T extends AbstractContainerMenu>
            extends AbstractContainerScreen<T>
    {
        protected static final int MACHINE_WIDTH = 176;
        protected static final int PANEL_WIDTH = 116;
        protected static final int TOTAL_WIDTH = MACHINE_WIDTH + PANEL_WIDTH;

        protected ThermalMachineScreen(
                T menu,
                Inventory inventory,
                Component title,
                int height
        )
        {
            super(menu, inventory, title, TOTAL_WIDTH, height);
            inventoryLabelY = height - 94;
        }

        @Override
        protected void init()
        {
            super.init();

            // Center the machine title over the 176px original GUI, not over
            // the appended status panel.
            titleLabelX =
                    (MACHINE_WIDTH - font.width(title)) / 2;
        }

        @Override
        public void extractBackground(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        )
        {
            super.extractBackground(
                    graphics,
                    mouseX,
                    mouseY,
                    partialTick
            );

            int panelX = leftPos + MACHINE_WIDTH;

            graphics.fill(
                    panelX,
                    topPos,
                    panelX + PANEL_WIDTH,
                    topPos + imageHeight,
                    0xFFD0D0D0
            );
            graphics.fill(
                    panelX,
                    topPos,
                    panelX + 1,
                    topPos + imageHeight,
                    0xFF4C4C4C
            );
            graphics.fill(
                    panelX + 7,
                    topPos + 7,
                    panelX + PANEL_WIDTH - 7,
                    topPos + imageHeight - 7,
                    0xFFB9B9B9
            );
            graphics.fill(
                    panelX + 8,
                    topPos + 8,
                    panelX + PANEL_WIDTH - 8,
                    topPos + imageHeight - 8,
                    0xFFC8C8C8
            );
        }

        protected void drawMachineBackground(
                GuiGraphicsExtractor graphics,
                Identifier texture,
                int visibleHeight
        )
        {
            // Original Cold Sweat GUIs are 256x256 atlases.
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    leftPos,
                    topPos,
                    0.0F,
                    0.0F,
                    MACHINE_WIDTH,
                    visibleHeight,
                    256,
                    256
            );
        }

        protected void drawFuelGauge(
                GuiGraphicsExtractor graphics,
                boolean hot,
                int x,
                int y,
                int fuel,
                int maxFuel
        )
        {
            Identifier empty = hot ? HOT_GAUGE_EMPTY : COLD_GAUGE_EMPTY;
            Identifier full = hot ? HOT_GAUGE : COLD_GAUGE;

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    empty,
                    x,
                    y,
                    0.0F,
                    0.0F,
                    14,
                    14,
                    14,
                    14
            );

            int gaugeHeight =
                    fuel <= 0
                            ? 0
                            : Math.max(
                                    2,
                                    Math.min(
                                            14,
                                            Math.round(
                                                    14.0F * fuel
                                                            / Math.max(1, maxFuel)
                                            )
                                    )
                            );

            if (gaugeHeight > 0)
            {
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        full,
                        x,
                        y + 14 - gaugeHeight,
                        0.0F,
                        14.0F - gaugeHeight,
                        14,
                        gaugeHeight,
                        14,
                        14
                );
            }
        }

        protected void panelText(
                GuiGraphicsExtractor graphics,
                String text,
                int y
        )
        {
            graphics.text(
                    font,
                    text,
                    leftPos + MACHINE_WIDTH + 10,
                    topPos + y,
                    0xFF303030,
                    false
            );
        }

        protected String formatRoom(int tenthsC)
        {
            if (tenthsC == HearthBlockEntity.ROOM_TEMP_UNAVAILABLE)
            {
                return "--";
            }

            return String.format(
                    Locale.ROOT,
                    "%.1f\u00B0C",
                    tenthsC / 10.0
            );
        }

        protected void drawReserveBar(
                GuiGraphicsExtractor graphics,
                String label,
                boolean hot,
                int fuel,
                int maxFuel,
                int labelY,
                int barY
        )
        {
            int x = leftPos + MACHINE_WIDTH + 10;
            int width = PANEL_WIDTH - 20;
            int height = 11;

            panelText(graphics, label, labelY);

            graphics.fill(
                    x,
                    topPos + barY,
                    x + width,
                    topPos + barY + height,
                    0xFF4E4E4E
            );

            graphics.fill(
                    x + 1,
                    topPos + barY + 1,
                    x + width - 1,
                    topPos + barY + height - 1,
                    0xFF777777
            );

            double fraction =
                    Math.max(
                            0.0,
                            Math.min(
                                    1.0,
                                    fuel / (double) Math.max(1, maxFuel)
                            )
                    );

            int innerWidth =
                    Math.max(
                            0,
                            (int) Math.round(
                                    (width - 2) * fraction
                            )
                    );

            if (innerWidth > 0)
            {
                graphics.fill(
                        x + 1,
                        topPos + barY + 1,
                        x + 1 + innerWidth,
                        topPos + barY + height - 1,
                        hot
                                ? 0xFFE16A2D
                                : 0xFF55BCEB
                );
            }

            String value = fuel + " / " + maxFuel;
            int textX =
                    x + width / 2
                            - font.width(value) / 2;

            graphics.text(
                    font,
                    value,
                    textX,
                    topPos + barY + 1,
                    0xFFFFFFFF,
                    true
            );
        }

        protected void sendMenuButton(int id)
        {
            if (minecraft != null
                    && minecraft.gameMode != null)
            {
                minecraft.gameMode.handleInventoryButtonClick(
                        menu.containerId,
                        id
                );
            }
        }
    }

    private static final class HearthScreen
            extends ThermalMachineScreen<HearthMenu>
    {
        private Button climateButton;

        private HearthScreen(
                HearthMenu menu,
                Inventory inventory,
                Component title
        )
        {
            super(menu, inventory, title, 166);
        }

        @Override
        protected void init()
        {
            super.init();

            climateButton =
                    addRenderableWidget(
                            Button.builder(
                                    Component.literal("Climate"),
                                    button -> sendMenuButton(0)
                            )
                            .bounds(
                                    leftPos + 186,
                                    topPos + 27,
                                    96,
                                    20
                            )
                            .build()
                    );

            addRenderableWidget(
                    Button.builder(
                            Component.literal("-1 C"),
                            button -> sendMenuButton(1)
                    )
                    .bounds(
                            leftPos + 186,
                            topPos + 52,
                            46,
                            20
                    )
                    .build()
            );

            addRenderableWidget(
                    Button.builder(
                            Component.literal("+1 C"),
                            button -> sendMenuButton(2)
                    )
                    .bounds(
                            leftPos + 236,
                            topPos + 52,
                            46,
                            20
                    )
                    .build()
            );
        }

        @Override
        public void extractBackground(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        )
        {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);

            drawMachineBackground(graphics, HEARTH_GUI, 166);

            drawFuelGauge(
                    graphics,
                    true,
                    leftPos + 62,
                    topPos + 49,
                    menu.getHotFuel(),
                    menu.getMaxFuel()
            );

            drawFuelGauge(
                    graphics,
                    false,
                    leftPos + 100,
                    topPos + 49,
                    menu.getColdFuel(),
                    menu.getMaxFuel()
            );

            climateButton.setMessage(
                    Component.literal(
                            "Climate: "
                                    + (menu.isClimateEnabled() ? "ON" : "OFF")
                    )
            );

            panelText(
                    graphics,
                    "Target: " + formatRoom(menu.getClimateTargetTenthsC()),
                    80
            );
            panelText(
                    graphics,
                    "Room: " + formatRoom(menu.getRoomTemperatureTenthsC()),
                    94
            );

            String state =
                    menu.getActiveMode() > 0
                            ? "Heating"
                            : menu.getActiveMode() < 0
                                    ? "Cooling"
                                    : "Idle";

            panelText(graphics, "State: " + state, 108);

            drawReserveBar(
                    graphics,
                    "Hot reserve",
                    true,
                    menu.getHotFuel(),
                    menu.getMaxFuel(),
                    119,
                    129
            );

            drawReserveBar(
                    graphics,
                    "Cold reserve",
                    false,
                    menu.getColdFuel(),
                    menu.getMaxFuel(),
                    143,
                    153
            );
        }
    }

    private static final class BoilerScreen
            extends ThermalMachineScreen<BoilerMenu>
    {
        private Button powerButton;

        private BoilerScreen(
                BoilerMenu menu,
                Inventory inventory,
                Component title
        )
        {
            super(menu, inventory, title, 172);
        }

        @Override
        protected void init()
        {
            super.init();

            powerButton =
                    addRenderableWidget(
                            Button.builder(
                                    Component.literal("Power"),
                                    button -> sendMenuButton(0)
                            )
                            .bounds(
                                    leftPos + 186,
                                    topPos + 29,
                                    96,
                                    20
                            )
                            .build()
                    );
        }

        @Override
        public void extractBackground(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        )
        {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);

            drawMachineBackground(graphics, BOILER_GUI, 172);

            drawFuelGauge(
                    graphics,
                    true,
                    leftPos + 100,
                    topPos + 63,
                    menu.getFuel(),
                    menu.getMaxFuel()
            );

            powerButton.setMessage(
                    Component.literal(
                            "Power: "
                                    + (menu.isPowerEnabled() ? "ON" : "OFF")
                    )
            );

            panelText(
                    graphics,
                    "Room: " + formatRoom(menu.getRoomTemperatureTenthsC()),
                    62
            );
            panelText(
                    graphics,
                    "State: " + (menu.isActive() ? "Heating" : "Idle"),
                    78
            );
            panelText(
                    graphics,
                    "Outlet: " + (menu.hasOutlet() ? "Ready" : "Missing"),
                    94
            );

            drawReserveBar(
                    graphics,
                    "Heat reserve",
                    true,
                    menu.getFuel(),
                    menu.getMaxFuel(),
                    116,
                    128
            );
        }
    }

    private static final class IceboxScreen
            extends ThermalMachineScreen<IceboxMenu>
    {
        private Button powerButton;

        private IceboxScreen(
                IceboxMenu menu,
                Inventory inventory,
                Component title
        )
        {
            super(menu, inventory, title, 172);
        }

        @Override
        protected void init()
        {
            super.init();

            powerButton =
                    addRenderableWidget(
                            Button.builder(
                                    Component.literal("Power"),
                                    button -> sendMenuButton(0)
                            )
                            .bounds(
                                    leftPos + 186,
                                    topPos + 29,
                                    96,
                                    20
                            )
                            .build()
                    );
        }

        @Override
        public void extractBackground(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        )
        {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);

            drawMachineBackground(graphics, ICEBOX_GUI, 172);

            drawFuelGauge(
                    graphics,
                    false,
                    leftPos + 100,
                    topPos + 63,
                    menu.getFuel(),
                    menu.getMaxFuel()
            );

            powerButton.setMessage(
                    Component.literal(
                            "Power: "
                                    + (menu.isPowerEnabled() ? "ON" : "OFF")
                    )
            );

            panelText(
                    graphics,
                    "Room: " + formatRoom(menu.getRoomTemperatureTenthsC()),
                    62
            );
            panelText(
                    graphics,
                    "State: " + (menu.isActive() ? "Cooling" : "Idle"),
                    78
            );
            panelText(
                    graphics,
                    "Outlet: " + (menu.hasOutlet() ? "Ready" : "Missing"),
                    94
            );

            drawReserveBar(
                    graphics,
                    "Cold reserve",
                    false,
                    menu.getFuel(),
                    menu.getMaxFuel(),
                    116,
                    128
            );
        }
    }

    public ColdSweatFabricClient()
    {
    }
}
