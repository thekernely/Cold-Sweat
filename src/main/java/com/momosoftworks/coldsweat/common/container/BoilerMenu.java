package com.momosoftworks.coldsweat.common.container;

import com.momosoftworks.coldsweat.api.registry.ThermalFuelRegistry;
import com.momosoftworks.coldsweat.common.blockentity.BoilerBlockEntity;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.core.init.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class BoilerMenu extends AbstractContainerMenu
{
    public final BoilerBlockEntity blockEntity;

    private int fuel;
    private int powerEnabled;
    private int roomTemperatureTenthsC =
            HearthBlockEntity.ROOM_TEMP_UNAVAILABLE;
    private int active;
    private int outlet;

    public BoilerMenu(
            int containerId,
            Inventory playerInventory,
            BoilerBlockEntity blockEntity
    )
    {
        super(ModMenus.BOILER, containerId);
        this.blockEntity = blockEntity;

        // Original Cold Sweat layout: fuel goes in the LOWER single slot.
        addSlot(
                new Slot(blockEntity, 0, 80, 62)
                {
                    @Override
                    public boolean mayPlace(ItemStack stack)
                    {
                        return ThermalFuelRegistry.getBoilerFuel(stack) > 0;
                    }
                }
        );

        // Waterskin row stays at the TOP.
        for (int slot = 1; slot < 10; slot++)
        {
            addSlot(
                    new Slot(
                            blockEntity,
                            slot,
                            -10 + slot * 18,
                            35
                    )
                    {
                        @Override
                        public boolean mayPlace(ItemStack stack)
                        {
                            return stack.is(ModItems.FILLED_WATERSKIN);
                        }
                    }
            );
        }

        addPlayerInventory(playerInventory);

        addDataSlot(sync(blockEntity::getFuel, value -> fuel = value));
        addDataSlot(sync(
                () -> blockEntity.isPowerEnabled() ? 1 : 0,
                value -> powerEnabled = value
        ));
        addDataSlot(sync(
                blockEntity::getRoomTemperatureTenthsForMenu,
                value -> roomTemperatureTenthsC = value
        ));
        addDataSlot(sync(
                () -> blockEntity.isUsingThermalHeat() ? 1 : 0,
                value -> active = value
        ));
        addDataSlot(sync(
                () -> blockEntity.hasUsableThermalOutlet() ? 1 : 0,
                value -> outlet = value
        ));
    }

    private static DataSlot sync(IntGetter getter, IntSetter setter)
    {
        return new DataSlot()
        {
            @Override
            public int get()
            {
                return getter.get();
            }

            @Override
            public void set(int value)
            {
                setter.set(value);
            }
        };
    }

    private void addPlayerInventory(Inventory inventory)
    {
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                addSlot(
                        new Slot(
                                inventory,
                                column + row * 9 + 9,
                                8 + column * 18,
                                91 + row * 18
                        )
                );
            }
        }

        for (int column = 0; column < 9; column++)
        {
            addSlot(
                    new Slot(
                            inventory,
                            column,
                            8 + column * 18,
                            149
                    )
            );
        }
    }

    @Override
    public boolean stillValid(Player player)
    {
        return blockEntity.stillValid(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        if (id == 0)
        {
            blockEntity.setPowerEnabled(
                    !blockEntity.isPowerEnabled()
            );
            return true;
        }
        return false;
    }

    public int getFuel()
    {
        return fuel;
    }

    public int getMaxFuel()
    {
        return blockEntity.getMaxFuel();
    }

    public boolean isPowerEnabled()
    {
        return powerEnabled != 0;
    }

    public int getRoomTemperatureTenthsC()
    {
        return roomTemperatureTenthsC;
    }

    public boolean isActive()
    {
        return active != 0;
    }

    public boolean hasOutlet()
    {
        return outlet != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (!slot.hasItem())
        {
            return result;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < 10)
        {
            if (!moveItemStackTo(stack, 10, 46, true))
            {
                return ItemStack.EMPTY;
            }
        }
        /*
         * Fuel gets first refusal. If anything is valid for more than one
         * machine slot, it still goes to the visible lower fuel slot.
         */
        else if (ThermalFuelRegistry.getBoilerFuel(stack) > 0)
        {
            if (!moveItemStackTo(stack, 0, 1, false))
            {
                return ItemStack.EMPTY;
            }
        }
        else if (stack.is(ModItems.FILLED_WATERSKIN))
        {
            if (!moveItemStackTo(stack, 1, 10, false))
            {
                return ItemStack.EMPTY;
            }
        }
        else if (index >= 10 && index < 37)
        {
            if (!moveItemStackTo(stack, 37, 46, false))
            {
                return ItemStack.EMPTY;
            }
        }
        else if (index >= 37 && index < 46)
        {
            if (!moveItemStackTo(stack, 10, 37, false))
            {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty())
        {
            slot.set(ItemStack.EMPTY);
        }
        else
        {
            slot.setChanged();
        }

        if (stack.getCount() == result.getCount())
        {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return result;
    }

    @FunctionalInterface
    private interface IntGetter
    {
        int get();
    }

    @FunctionalInterface
    private interface IntSetter
    {
        void set(int value);
    }
}
