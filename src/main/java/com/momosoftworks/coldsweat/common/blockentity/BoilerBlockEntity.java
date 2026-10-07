package com.momosoftworks.coldsweat.common.blockentity;

import com.momosoftworks.coldsweat.api.registry.ThermalFuelRegistry;
import com.momosoftworks.coldsweat.common.block.BoilerBlock;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

public class BoilerBlockEntity extends HearthBlockEntity
{
    private static final int WATERSKIN_INTERVAL = 20;
    private static final double MAX_WATER_TEMPERATURE = 50.0;

    private boolean usingThermalHeat;
    private boolean powerEnabled;

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

        /*
         * M9.3c9b:
         * The GUI power switch is the normal control path.
         * Redstone is NOT required. The smokestack remains the physical
         * room-air outlet requirement.
         */
        boolean wantsRoomHeat =
                blockEntity.powerEnabled
                        && blockEntity.hasThermalOutlet(level);

        boolean processingWaterskin =
                blockEntity.powerEnabled
                        && blockEntity.hasWaterskinBelowTarget();

        if (blockEntity.getTicksExisted() % FUEL_INTERVAL == 0
                && (wantsRoomHeat || processingWaterskin))
        {
            blockEntity.tryLoadFuel();
        }

        blockEntity.usingThermalHeat =
                wantsRoomHeat
                        && blockEntity.getFuel() > 0;

        if (processingWaterskin
                && blockEntity.getFuel() > 0
                && blockEntity.getTicksExisted() % WATERSKIN_INTERVAL == 0)
        {
            blockEntity.warmWaterskins();
        }

        if (blockEntity.getTicksExisted() % EFFECT_INTERVAL == 0)
        {
            blockEntity.spawnThermalAirParticles(
                    level,
                    blockEntity.usingThermalHeat,
                    false
            );
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
                activeDemand
                        && blockEntity.getFuel() > 0;

        if (state.getValue(BoilerBlock.LIT) != lit)
        {
            level.setBlock(
                    pos,
                    state.setValue(BoilerBlock.LIT, lit),
                    3
            );
            level.getLightEngine().checkBlock(pos);
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
        return List.of(Direction.values());
    }

    @Override
    protected List<Direction> getCoolingSides()
    {
        return List.of();
    }

    public boolean hasUsableThermalOutlet()
    {
        return level != null
                && hasThermalOutlet(level);
    }

    public boolean isPowerEnabled()
    {
        return powerEnabled;
    }

    public void setPowerEnabled(boolean enabled)
    {
        powerEnabled = enabled;
        setChanged();
    }

    public boolean isUsingThermalHeat()
    {
        return usingThermalHeat;
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

    /**
     * Vanilla-like burn buffer:
     * only consume one visible fuel item when the reservoir is empty AND the
     * powered machine actually needs energy.
     */
    private void tryLoadFuel()
    {
        if (getFuel() > 0)
        {
            return;
        }

        ItemStack fuelStack = getItem(0);
        int fuelValue = ThermalFuelRegistry.getBoilerFuel(fuelStack);

        if (fuelValue <= 0)
        {
            return;
        }

        setFuel(
                Math.min(
                        getMaxFuel(),
                        fuelValue
                )
        );

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

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putBoolean("PowerEnabled", powerEnabled);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        powerEnabled = input.getBooleanOr("PowerEnabled", false);
    }
}
