package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.api.registry.BlockTempRegistry;
import com.momosoftworks.coldsweat.api.temperature.block_temp.ThermalMachineBlockTemp;
import com.momosoftworks.coldsweat.common.block.BoilerBlock;
import com.momosoftworks.coldsweat.common.block.IceboxBlock;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;

/**
 * M6 block-temperature defaults that depend on registered thermal machines.
 */
public final class MachineBlockTempInit
{
    public static void initialize()
    {
        BlockTempRegistry.register(
                new ThermalMachineBlockTemp(
                        ModBlocks.BOILER,
                        15.0,
                        7.0,
                        36.0,
                        212.0,
                        state -> state.getValue(BoilerBlock.LIT)
                )
        );

        BlockTempRegistry.register(
                new ThermalMachineBlockTemp(
                        ModBlocks.ICEBOX,
                        -15.0,
                        7.0,
                        36.0,
                        32.0,
                        state -> state.getValue(IceboxBlock.FROSTED)
                )
        );

        ColdSweatFabric.LOGGER.info(
                "Registered Boiler and Icebox block-temperature defaults."
        );
    }

    private MachineBlockTempInit()
    {
    }
}
