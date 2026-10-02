package com.momosoftworks.coldsweat.common.container;

import com.momosoftworks.coldsweat.api.registry.ThermalFuelRegistry;
import com.momosoftworks.coldsweat.common.blockentity.BoilerBlockEntity;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.core.init.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class BoilerMenu extends AbstractContainerMenu
{
    public final BoilerBlockEntity blockEntity;

    public BoilerMenu(
            int containerId,
            Inventory playerInventory,
            BoilerBlockEntity blockEntity
    )
    {
        super(ModMenus.BOILER, containerId);
        this.blockEntity = blockEntity;

        addSlot(new Slot(blockEntity, 0, 80, 17)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return ThermalFuelRegistry.getBoilerFuel(stack) > 0;
            }
        });

        for (int slot = 1; slot < 10; slot++)
        {
            addSlot(new Slot(
                    blockEntity,
                    slot,
                    8 + (slot - 1) * 18,
                    42
            )
            {
                @Override
                public boolean mayPlace(ItemStack stack)
                {
                    return stack.is(ModItems.FILLED_WATERSKIN);
                }
            });
        }

        addPlayerInventory(playerInventory);
    }

    private void addPlayerInventory(Inventory inventory)
    {
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        8 + column * 18,
                        84 + row * 18
                ));
            }
        }

        for (int column = 0; column < 9; column++)
        {
            addSlot(new Slot(
                    inventory,
                    column,
                    8 + column * 18,
                    142
            ));
        }
    }

    @Override
    public boolean stillValid(Player player)
    {
        return blockEntity.stillValid(player);
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
        else if (stack.is(ModItems.FILLED_WATERSKIN))
        {
            if (!moveItemStackTo(stack, 1, 10, false))
            {
                return ItemStack.EMPTY;
            }
        }
        else if (ThermalFuelRegistry.getBoilerFuel(stack) > 0)
        {
            if (!moveItemStackTo(stack, 0, 1, false))
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
}
