package com.momosoftworks.coldsweat.common.blockentity;

import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class IceboxBlockEntity extends HearthBlockEntity
{
    public IceboxBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.ICEBOX, pos, state);
    }

    public static void tick(
            Level level,
            BlockPos pos,
            BlockState state,
            IceboxBlockEntity blockEntity
    )
    {
        HearthBlockEntity.tick(level, pos, state, blockEntity);
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
