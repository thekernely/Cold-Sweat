package com.momosoftworks.coldsweat.common.blockentity;

import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BoilerBlockEntity extends HearthBlockEntity
{
    public BoilerBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.BOILER, pos, state);
    }

    public static void tick(
            Level level,
            BlockPos pos,
            BlockState state,
            BoilerBlockEntity blockEntity
    )
    {
        HearthBlockEntity.tick(level, pos, state, blockEntity);
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
