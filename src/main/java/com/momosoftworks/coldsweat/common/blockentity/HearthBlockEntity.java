package com.momosoftworks.coldsweat.common.blockentity;

import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Shared server-side state foundation for Cold Sweat's thermal machines.
 *
 * M6.1 intentionally restores the durable state boundary first. Spread paths,
 * fuel ingestion, fluid transfer, menus and thermal effects layer onto this
 * class in later M6 slices without changing the persisted hot/cold fuel model.
 */
public class HearthBlockEntity extends BlockEntity
{
    public static final int MAX_FUEL = 1000;

    private int hotFuel;
    private int coldFuel;
    private int ticksExisted;

    public HearthBlockEntity(BlockPos pos, BlockState state)
    {
        this(ModBlockEntities.HEARTH, pos, state);
    }

    protected HearthBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    )
    {
        super(type, pos, state);
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
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putInt("HotFuel", hotFuel);
        output.putInt("ColdFuel", coldFuel);
        output.putInt("TicksExisted", ticksExisted);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        hotFuel = clampFuel(input.getIntOr("HotFuel", 0));
        coldFuel = clampFuel(input.getIntOr("ColdFuel", 0));
        ticksExisted = Math.max(0, input.getIntOr("TicksExisted", 0));
    }
}
