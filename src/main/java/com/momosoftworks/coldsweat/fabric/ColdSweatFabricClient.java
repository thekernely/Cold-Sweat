package com.momosoftworks.coldsweat.fabric;

import com.momosoftworks.coldsweat.common.container.BoilerMenu;
import com.momosoftworks.coldsweat.common.container.HearthMenu;
import com.momosoftworks.coldsweat.common.container.IceboxMenu;
import com.momosoftworks.coldsweat.core.init.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

public final class ColdSweatFabricClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        MenuScreens.register(ModMenus.HEARTH, HearthScreen::new);
        MenuScreens.register(ModMenus.BOILER, BoilerScreen::new);
        MenuScreens.register(ModMenus.ICEBOX, IceboxScreen::new);
    }

    private abstract static class ThermalMachineScreen<T extends AbstractContainerMenu>
            extends AbstractContainerScreen<T>
    {
        protected ThermalMachineScreen(
                T menu,
                Inventory inventory,
                Component title
        )
        {
            super(menu, inventory, title, 176, 166);
            inventoryLabelY = 72;
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

            int x = leftPos;
            int y = topPos;

            graphics.fill(
                    x,
                    y,
                    x + imageWidth,
                    y + imageHeight,
                    0xFFC6C6C6
            );
            graphics.fill(x, y, x + imageWidth, y + 1, 0xFF373737);
            graphics.fill(x, y, x + 1, y + imageHeight, 0xFF373737);
            graphics.fill(
                    x,
                    y + imageHeight - 1,
                    x + imageWidth,
                    y + imageHeight,
                    0xFFFFFFFF
            );
            graphics.fill(
                    x + imageWidth - 1,
                    y,
                    x + imageWidth,
                    y + imageHeight,
                    0xFFFFFFFF
            );

            for (Slot slot : menu.slots)
            {
                int sx = x + slot.x - 1;
                int sy = y + slot.y - 1;

                graphics.fill(
                        sx,
                        sy,
                        sx + 18,
                        sy + 18,
                        0xFF555555
                );
                graphics.fill(
                        sx + 1,
                        sy + 1,
                        sx + 17,
                        sy + 17,
                        0xFF8B8B8B
                );
            }
        }
    }

    private static final class HearthScreen
            extends ThermalMachineScreen<HearthMenu>
    {
        private HearthScreen(
                HearthMenu menu,
                Inventory inventory,
                Component title
        )
        {
            super(menu, inventory, title);
        }
    }

    private static final class BoilerScreen
            extends ThermalMachineScreen<BoilerMenu>
    {
        private BoilerScreen(
                BoilerMenu menu,
                Inventory inventory,
                Component title
        )
        {
            super(menu, inventory, title);
        }
    }

    private static final class IceboxScreen
            extends ThermalMachineScreen<IceboxMenu>
    {
        private IceboxScreen(
                IceboxMenu menu,
                Inventory inventory,
                Component title
        )
        {
            super(menu, inventory, title);
        }
    }

    public ColdSweatFabricClient()
    {
    }
}
