package com.momosoftworks.coldsweat.fabric.temperature;

import com.momosoftworks.coldsweat.common.blockentity.BoilerBlockEntity;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.common.blockentity.IceboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bridges Cold Sweat thermal machines into the retained-room-air model.
 *
 * Signed source power:
 * - positive = heating
 * - negative = cooling
 *
 * A machine contributes only while its own runtime is actively heating or
 * cooling. Fuel, redstone and Smokestack semantics therefore remain owned by
 * the machine rather than being duplicated by the room scanner.
 */
public final class ThermalMachineRoomSource
{
    private static final double HEARTH_POWER = 55.0;
    private static final double BOILER_POWER = 38.0;
    private static final double ICEBOX_POWER = -24.0;

    private ThermalMachineRoomSource()
    {
    }

    public static double getPower(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    )
    {
        BlockEntity blockEntity =
                level.getBlockEntity(pos);

        if (blockEntity instanceof BoilerBlockEntity boiler)
        {
            return boiler.isUsingThermalHeat()
                    ? BOILER_POWER
                    : 0.0;
        }

        if (blockEntity instanceof IceboxBlockEntity icebox)
        {
            return icebox.isUsingThermalCold()
                    ? ICEBOX_POWER
                    : 0.0;
        }

        if (blockEntity instanceof HearthBlockEntity hearth)
        {
            double power = 0.0;

            if (hearth.isUsingHotFuel())
            {
                power += HEARTH_POWER;
            }

            if (hearth.isUsingColdFuel())
            {
                power -= HEARTH_POWER;
            }

            return power;
        }

        return 0.0;
    }
}