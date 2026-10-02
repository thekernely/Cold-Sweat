package com.momosoftworks.coldsweat.common.blockentity;

import com.momosoftworks.coldsweat.api.registry.ThermalFuelRegistry;
import com.momosoftworks.coldsweat.common.block.BoilerBlock;
import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import com.momosoftworks.coldsweat.core.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BoilerBlockEntity extends HearthBlockEntity
{
    private static final int WATERSKIN_INTERVAL = 20;
    private static final double MAX_WATER_TEMPERATURE = 50.0;

    private boolean usingThermalHeat;

    public BoilerBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.BOILER, pos, state, 10);
    }

    public static void tick(
            Level level,
            BlockPos pos,
            BlockState state,
            BoilerBlockEntity blockEntity
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
                blockEntity.hasWaterskinBelowTarget();

        if (blockEntity.getFuel() > 0
                && blockEntity.getTicksExisted() % WATERSKIN_INTERVAL == 0)
        {
            blockEntity.warmWaterskins();
        }

        if (blockEntity.getTicksExisted() % EFFECT_INTERVAL == 0)
        {
            blockEntity.usingThermalHeat =
                    blockEntity.provideThermalEffects(
                            level,
                            pos,
                            true,
                            false,
                            5
                    ).hot();
        }

        boolean activeDemand =
                processingWaterskin
                        || blockEntity.usingThermalHeat;

        if (activeDemand
                && blockEntity.getFuel() > 0
                && blockEntity.getTicksExisted() % FUEL_INTERVAL == 0)
        {
            blockEntity.setFuel(blockEntity.getFuel() - 1);
        }

        boolean lit =
                blockEntity.getFuel() > 0
                        && activeDemand;

        if (state.getValue(BoilerBlock.LIT) != lit)
        {
            level.setBlock(
                    pos,
                    state.setValue(BoilerBlock.LIT, lit),
                    3
            );
        }
    }

    private boolean hasWaterskinBelowTarget()
    {
        for (int slot = 1; slot < getContainerSize(); slot++)
        {
            ItemStack stack = getItem(slot);
            if (stack.is(ModItems.FILLED_WATERSKIN)
                    && stack.getOrDefault(
                            ModItemComponents.WATER_TEMPERATURE,
                            0.0
                    ) < MAX_WATER_TEMPERATURE)
            {
                return true;
            }
        }
        return false;
    }

    private void warmWaterskins()
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

            if (temperature < MAX_WATER_TEMPERATURE)
            {
                stack.set(
                        ModItemComponents.WATER_TEMPERATURE,
                        Math.min(
                                MAX_WATER_TEMPERATURE,
                                temperature + 1.0
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
                ThermalFuelRegistry.getBoilerFuel(fuelStack);

        if (fuelValue <= 0
                || getFuel() > getMaxFuel() - fuelValue)
        {
            return;
        }

        setFuel(getFuel() + fuelValue);

        if (fuelStack.is(Items.LAVA_BUCKET))
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
        return getHotFuel();
    }

    public void setFuel(int amount)
    {
        setHotFuel(amount);
    }

    public void addFuel(int amount)
    {
        addHotFuel(amount);
    }
}
