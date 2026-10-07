package com.momosoftworks.coldsweat.common.container;

import com.momosoftworks.coldsweat.api.registry.ThermalFuelRegistry;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.core.init.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class HearthMenu extends AbstractContainerMenu
{
    public final HearthBlockEntity blockEntity;

    private int hotFuel;
    private int coldFuel;
    private int climateEnabled;
    private int climateTargetTenthsC;
    private int roomTemperatureTenthsC = HearthBlockEntity.ROOM_TEMP_UNAVAILABLE;
    private int activeMode;

    public HearthMenu(int containerId, Inventory playerInventory, HearthBlockEntity blockEntity)
    {
        super(ModMenus.HEARTH, containerId);
        this.blockEntity = blockEntity;

        addSlot(new Slot(
                        blockEntity,
                        0,
                        80,
                        48
                )
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return ThermalFuelRegistry.getHearthFuel(stack) != 0;
            }
        });

        addPlayerInventory(playerInventory);

        addDataSlot(sync(blockEntity::getHotFuel, value -> hotFuel = value));
        addDataSlot(sync(blockEntity::getColdFuel, value -> coldFuel = value));
        addDataSlot(sync(() -> blockEntity.isClimateControlEnabled() ? 1 : 0,
                value -> climateEnabled = value));
        addDataSlot(sync(blockEntity::getClimateTargetTenthsC,
                value -> climateTargetTenthsC = value));
        addDataSlot(sync(blockEntity::getRoomTemperatureTenthsForMenu,
                value -> roomTemperatureTenthsC = value));
        addDataSlot(sync(blockEntity::getThermostatMode,
                value -> activeMode = value));
    }

    private static DataSlot sync(IntGetter getter, IntSetter setter)
    {
        return new DataSlot()
        {
            @Override public int get() { return getter.get(); }
            @Override public void set(int value) { setter.set(value); }
        };
    }

    private void addPlayerInventory(Inventory inventory)
    {
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++)
        {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
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
            blockEntity.setClimateControlEnabled(!blockEntity.isClimateControlEnabled());
            return true;
        }
        if (id == 1)
        {
            blockEntity.adjustClimateTargetC(-1);
            return true;
        }
        if (id == 2)
        {
            blockEntity.adjustClimateTargetC(1);
            return true;
        }
        return false;
    }

    public int getHotFuel() { return hotFuel; }
    public int getColdFuel() { return coldFuel; }
    public int getMaxFuel() { return blockEntity.getMaxFuel(); }
    public boolean isClimateEnabled() { return climateEnabled != 0; }
    public int getClimateTargetTenthsC() { return climateTargetTenthsC; }
    public int getRoomTemperatureTenthsC() { return roomTemperatureTenthsC; }
    public int getActiveMode() { return activeMode; }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index == 0)
        {
            if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        }
        else if (getSlot(0).mayPlace(stack))
        {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        }
        else if (index >= 1 && index < 28)
        {
            if (!moveItemStackTo(stack, 28, 37, false)) return ItemStack.EMPTY;
        }
        else if (index >= 28 && index < 37)
        {
            if (!moveItemStackTo(stack, 1, 28, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }

    @FunctionalInterface private interface IntGetter { int get(); }
    @FunctionalInterface private interface IntSetter { void set(int value); }
}
