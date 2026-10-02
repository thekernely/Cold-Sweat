package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.api.registry.BlockTempRegistry;
import com.momosoftworks.coldsweat.api.temperature.block_temp.FurnaceBlockTemp;
import com.momosoftworks.coldsweat.api.temperature.block_temp.NetherPortalBlockTemp;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;

/**
 * Fabric-native block-temperature source bootstrap.
 */
public final class BlockTempInit
{
    public static void initialize()
    {
        BlockTempRegistry.flush();

        BlockTempRegistry.register(
                new FurnaceBlockTemp()
        );
        BlockTempRegistry.register(
                new NetherPortalBlockTemp()
        );

        ColdSweatFabric.LOGGER.info(
                "Registered {} Cold Sweat block temperature source type(s).",
                BlockTempRegistry.getEntries().size()
        );
    }

    private BlockTempInit()
    {
    }
}
