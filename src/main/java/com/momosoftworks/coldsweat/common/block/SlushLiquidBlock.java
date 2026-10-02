package com.momosoftworks.coldsweat.common.block;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;

public final class SlushLiquidBlock extends LiquidBlock
{
    public SlushLiquidBlock(
            FlowingFluid fluid,
            BlockBehaviour.Properties properties
    )
    {
        super(fluid, properties);
    }
}
