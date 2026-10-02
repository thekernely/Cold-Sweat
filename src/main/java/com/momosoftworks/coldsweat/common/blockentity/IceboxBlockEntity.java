package com.momosoftworks.coldsweat.common.blockentity;

import com.momosoftworks.coldsweat.api.registry.ThermalFuelRegistry;
import com.momosoftworks.coldsweat.common.block.IceboxBlock;
import com.momosoftworks.coldsweat.common.block.SmokestackBlock;
import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import com.momosoftworks.coldsweat.core.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class IceboxBlockEntity extends HearthBlockEntity
{
    private static final int WATERSKIN_INTERVAL = 20;
    private static final double MIN_WATER_TEMPERATURE = -50.0;

    private boolean usingThermalCold;

    public IceboxBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.ICEBOX, pos, state, 10);
    }

    public static void tick(
            Level level,
            BlockPos pos,
            BlockState state,
            IceboxBlockEntity blockEntity
    )
    {
        blockEntity.tickCommon();

        if (level.isClientSide())
        {
            return;
        }

        if (blockEntity.getTicksExisted() % FUEL_INTERVAL == 0)
        {
            blockEntity.tryLoadFuel();
        }

        boolean processingWaterskin =
                blockEntity.hasWaterskinAboveTarget();

        if (blockEntity.getFuel() > 0
                && blockEntity.getTicksExisted() % WATERSKIN_INTERVAL == 0)
        {
            blockEntity.coolWaterskins();
        }

        if (blockEntity.getTicksExisted() % EFFECT_INTERVAL == 0)
        {
            blockEntity.usingThermalCold =
                    blockEntity.hasThermalOutlet(level)
                            && blockEntity.hasCoolingSignal(level)
                            && blockEntity.getFuel() > 0;

            blockEntity.provideThermalEffects(
                    level,
                    pos,
                    false,
                    blockEntity.usingThermalCold,
                    5
            );
        }

        boolean activeDemand =
                processingWaterskin
                        || blockEntity.usingThermalCold;

        if (activeDemand
                && blockEntity.getFuel() > 0
                && blockEntity.getTicksExisted() % FUEL_INTERVAL == 0)
        {
            blockEntity.setFuel(blockEntity.getFuel() - 1);
        }

        boolean frosted = blockEntity.getFuel() > 0;

        if (state.getValue(IceboxBlock.FROSTED) != frosted)
        {
            level.setBlock(
                    pos,
                    state.setValue(IceboxBlock.FROSTED, frosted),
                    3
            );
        }
    }

    @Override
    protected boolean hasThermalOutlet(Level level)
    {
        return level.getBlockState(getBlockPos().above()).getBlock()
                instanceof SmokestackBlock;
    }

    @Override
    protected List<Direction> getHeatingSides()
    {
        return List.of();
    }

    @Override
    protected List<Direction> getCoolingSides()
    {
        return List.of(
                Direction.NORTH,
                Direction.EAST,
                Direction.SOUTH,
                Direction.WEST,
                Direction.DOWN
        );
    }

    private boolean hasWaterskinAboveTarget()
    {
        for (int slot = 1; slot < getContainerSize(); slot++)
        {
            ItemStack stack = getItem(slot);
            if (stack.is(ModItems.FILLED_WATERSKIN)
                    && stack.getOrDefault(
                            ModItemComponents.WATER_TEMPERATURE,
                            0.0
                    ) > MIN_WATER_TEMPERATURE)
            {
                return true;
            }
        }
        return false;
    }

    private void coolWaterskins()
    {
        boolean changed = false;

        for (int slot = 1; slot < getContainerSize(); slot++)
        {
            ItemStack stack = getItem(slot);
            if (!stack.is(ModItems.FILLED_WATERSKIN))
            {
                continue;
            }

            double temperature = stack.getOrDefault(
                    ModItemComponents.WATER_TEMPERATURE,
                    0.0
            );

            if (temperature > MIN_WATER_TEMPERATURE)
            {
                stack.set(
                        ModItemComponents.WATER_TEMPERATURE,
                        Math.max(
                                MIN_WATER_TEMPERATURE,
                                temperature - 1.0
                        )
                );
                changed = true;
            }
        }

        if (changed)
        {
            setChanged();
        }
    }

    private void tryLoadFuel()
    {
        ItemStack fuelStack = getItem(0);
        int fuelValue =
                ThermalFuelRegistry.getIceboxFuel(fuelStack);

        if (fuelValue <= 0
                || getFuel() > getMaxFuel() - fuelValue)
        {
            return;
        }

        setFuel(getFuel() + fuelValue);

        if (fuelStack.is(Items.POWDER_SNOW_BUCKET))
        {
            setItem(0, new ItemStack(Items.BUCKET));
        }
        else
        {
            fuelStack.shrink(1);
            if (fuelStack.isEmpty())
            {
                setItem(0, ItemStack.EMPTY);
            }
            else
            {
                setChanged();
            }
        }
    }

    public int getFuel()
    {
        return getColdFuel();
    }

    public void setFuel(int amount)
    {
        setColdFuel(amount);
    }

    public void addFuel(int amount)
    {
        addColdFuel(amount);
    }
}
