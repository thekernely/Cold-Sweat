package com.momosoftworks.coldsweat.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Upper half of the Hearth. Multiblock placement/removal behavior is restored
 * in the next M6 behavior slice; the canonical block ID exists from M6.1.
 */
public class HearthTopBlock extends Block
{
    public HearthTopBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }
}
