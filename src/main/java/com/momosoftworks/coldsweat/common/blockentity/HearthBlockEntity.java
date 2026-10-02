package com.momosoftworks.coldsweat.common.blockentity;

import com.momosoftworks.coldsweat.api.registry.ThermalFuelRegistry;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureRuntime;
import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Shared server-side state and thermal-source runtime for the Hearth family.
 *
 * M6.3 restores the upstream Warmth/Frigidness delivery model in a direct
 * 16-block area. The full smokestack/path-spread topology remains a later M6
 * slice; this keeps the temperature semantics correct without pulling the
 * entire upstream spread graph in at once.
 */
public class HearthBlockEntity extends BlockEntity implements Container
{
    public static final int MAX_FUEL = 1000;

    protected static final int FUEL_INTERVAL = 40;
    protected static final int EFFECT_INTERVAL = 20;
    protected static final int THERMAL_RANGE = 16;
    protected static final int WARM_UP_TIME = 1200;

    private final NonNullList<ItemStack> items;

    private int hotFuel;
    private int coldFuel;
    private int ticksExisted;
    private int insulationLevel;

    private boolean usingHotFuel;
    private boolean usingColdFuel;

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
        blockEntity.tickCommon();

        if (level.isClientSide())
        {
            return;
        }

        if (blockEntity.getTicksExisted() % FUEL_INTERVAL == 0)
        {
            blockEntity.tryLoadHearthFuel();
        }

        if (blockEntity.getTicksExisted() % EFFECT_INTERVAL == 0)
        {
            ThermalUsage usage = blockEntity.provideThermalEffects(
                    level,
                    pos,
                    true,
                    true,
                    10
            );
            blockEntity.usingColdFuel = usage.cold();
            blockEntity.usingHotFuel = usage.hot();
        }

        if (blockEntity.getTicksExisted() % FUEL_INTERVAL == 0)
        {
            if (blockEntity.usingHotFuel)
            {
                blockEntity.setHotFuel(blockEntity.getHotFuel() - 1);
            }
            if (blockEntity.usingColdFuel)
            {
                blockEntity.setColdFuel(blockEntity.getColdFuel() - 1);
            }
        }
    }

    protected void tickCommon()
    {
        ticksExisted++;

        if (hasFuel() && insulationLevel < WARM_UP_TIME)
        {
            insulationLevel++;
        }
    }

    protected ThermalUsage provideThermalEffects(
            Level level,
            BlockPos pos,
            boolean allowHot,
            boolean allowCold,
            int maxStrength
    )
    {
        if ((!allowHot || getHotFuel() <= 0)
                && (!allowCold || getColdFuel() <= 0))
        {
            return ThermalUsage.NONE;
        }

        int amplifier = getThermalEffectAmplifier(maxStrength);
        boolean usedHot = false;
        boolean usedCold = false;

        AABB bounds = new AABB(pos).inflate(THERMAL_RANGE);

        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                candidate -> EntityTempManager.isTemperatureEnabled(candidate)
        ))
        {
            if (entity.isSpectator())
            {
                continue;
            }

            double worldTemperature = Temperature.get(
                    entity,
                    Temperature.Trait.WORLD
            );

            double freezingPoint = EntityTempManager.resolveAttributeValue(
                    entity,
                    Temperature.Trait.FREEZING_POINT,
                    TemperatureRuntime.DEFAULT_FREEZING_POINT
            );

            double burningPoint = EntityTempManager.resolveAttributeValue(
                    entity,
                    Temperature.Trait.BURNING_POINT,
                    TemperatureRuntime.DEFAULT_BURNING_POINT
            );

            if (allowHot
                    && getHotFuel() > 0
                    && worldTemperature < freezingPoint)
            {
                entity.addEffect(new MobEffectInstance(
                        ModEffects.WARMTH,
                        60,
                        amplifier,
                        false,
                        false,
                        true
                ));
                usedHot = true;
            }

            if (allowCold
                    && getColdFuel() > 0
                    && worldTemperature > burningPoint)
            {
                entity.addEffect(new MobEffectInstance(
                        ModEffects.FRIGIDNESS,
                        60,
                        amplifier,
                        false,
                        false,
                        true
                ));
                usedCold = true;
            }
        }

        return new ThermalUsage(usedCold, usedHot);
    }

    protected int getThermalEffectAmplifier(int maxStrength)
    {
        int clampedStrength = Math.max(1, maxStrength);
        int maxAmplifier = clampedStrength - 1;

        if (maxAmplifier == 0 || WARM_UP_TIME <= 0)
        {
            return maxAmplifier;
        }

        double progress = Math.min(
                1.0,
                insulationLevel / (double) WARM_UP_TIME
        );

        return Math.min(
                maxAmplifier,
                (int) Math.floor(progress * maxAmplifier)
        );
    }

    private void tryLoadHearthFuel()
    {
        ItemStack fuelStack = getItem(0);
        int fuelValue = ThermalFuelRegistry.getHearthFuel(fuelStack);

        if (fuelValue == 0)
        {
            return;
        }

        int magnitude = Math.abs(fuelValue);
        int stored = fuelValue > 0 ? getHotFuel() : getColdFuel();

        if (stored > getMaxFuel() - magnitude)
        {
            return;
        }

        if (fuelValue > 0)
        {
            addHotFuel(magnitude);
        }
        else
        {
            addColdFuel(magnitude);
        }

        if (fuelStack.is(Items.LAVA_BUCKET)
                || fuelStack.is(Items.POWDER_SNOW_BUCKET))
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
        output.putInt("InsulationLevel", insulationLevel);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        hotFuel = clampFuel(input.getIntOr("HotFuel", 0));
        coldFuel = clampFuel(input.getIntOr("ColdFuel", 0));
        ticksExisted = Math.max(0, input.getIntOr("TicksExisted", 0));
        insulationLevel = Math.max(
                0,
                Math.min(
                        WARM_UP_TIME,
                        input.getIntOr("InsulationLevel", 0)
                )
        );
    }

    protected record ThermalUsage(boolean cold, boolean hot)
    {
        private static final ThermalUsage NONE =
                new ThermalUsage(false, false);
    }
}
