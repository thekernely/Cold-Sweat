package com.momosoftworks.coldsweat.common.blockentity;

import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Shared server-side state foundation for Cold Sweat's thermal machines.
 *
 * M6.2 adds the persistent container boundary used by the Hearth, Boiler and
 * Icebox. The Hearth owns one input slot; Boiler/Icebox expand that to ten.
 */
public class HearthBlockEntity extends BlockEntity implements Container
{
    public static final int MAX_FUEL = 1000;

    private final NonNullList<ItemStack> items;

    private int hotFuel;
    private int coldFuel;
    private int ticksExisted;

    public HearthBlockEntity(BlockPos pos, BlockState state)
    {
        this(ModBlockEntities.HEARTH, pos, state, 1);
    }

    protected HearthBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    )
    {
        this(type, pos, state, 1);
    }

    protected HearthBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state,
            int containerSize
    )
    {
        super(type, pos, state);
        items = NonNullList.withSize(Math.max(1, containerSize), ItemStack.EMPTY);
    }

    public static void tick(
            Level level,
            BlockPos pos,
            BlockState state,
            HearthBlockEntity blockEntity
    )
    {
        blockEntity.ticksExisted++;
    }

    public int getTicksExisted()
    {
        return ticksExisted;
    }

    public int getMaxFuel()
    {
        return MAX_FUEL;
    }

    public int getHotFuel()
    {
        return hotFuel;
    }

    public int getColdFuel()
    {
        return coldFuel;
    }

    public void setHotFuel(int amount)
    {
        hotFuel = clampFuel(amount);
        setChanged();
    }

    public void setColdFuel(int amount)
    {
        coldFuel = clampFuel(amount);
        setChanged();
    }

    public void addHotFuel(int amount)
    {
        setHotFuel(hotFuel + amount);
    }

    public void addColdFuel(int amount)
    {
        setColdFuel(coldFuel + amount);
    }

    public boolean hasFuel()
    {
        return hotFuel > 0 || coldFuel > 0;
    }

    private int clampFuel(int amount)
    {
        return Math.max(0, Math.min(getMaxFuel(), amount));
    }

    @Override
    public int getContainerSize()
    {
        return items.size();
    }

    @Override
    public boolean isEmpty()
    {
        for (ItemStack stack : items)
        {
            if (!stack.isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot)
    {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount)
    {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty())
        {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot)
    {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack)
    {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize())
        {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player)
    {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5
                ) <= 64.0;
    }

    @Override
    public void clearContent()
    {
        items.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("HotFuel", hotFuel);
        output.putInt("ColdFuel", coldFuel);
        output.putInt("TicksExisted", ticksExisted);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        hotFuel = clampFuel(input.getIntOr("HotFuel", 0));
        coldFuel = clampFuel(input.getIntOr("ColdFuel", 0));
        ticksExisted = Math.max(0, input.getIntOr("TicksExisted", 0));
    }
}
