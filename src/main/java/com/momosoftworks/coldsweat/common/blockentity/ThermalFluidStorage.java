package com.momosoftworks.coldsweat.common.blockentity;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.level.material.Fluids;

/**
 * Insertion-only Fabric Transfer API view of Cold Sweat's hot-fuel tank.
 *
 * Cold Sweat stores machine fuel in a 0..1000 internal scale. Fabric uses
 * droplets (81,000 per bucket), so one fuel point maps exactly to 81 droplets.
 */
public final class ThermalFluidStorage extends SingleFluidStorage
{
    private static final long DROPLETS_PER_FUEL =
            FluidConstants.BUCKET / HearthBlockEntity.MAX_FUEL;

    private final HearthBlockEntity owner;

    public ThermalFluidStorage(HearthBlockEntity owner)
    {
        this.owner = owner;
        syncFromFuel(owner.getHotFuel());
    }

    @Override
    protected long getCapacity(FluidVariant variant)
    {
        return FluidConstants.BUCKET;
    }

    @Override
    protected boolean canInsert(FluidVariant variant)
    {
        return variant.getFluid() == Fluids.LAVA;
    }

    @Override
    protected boolean canExtract(FluidVariant variant)
    {
        return false;
    }

    @Override
    public long insert(
            FluidVariant insertedVariant,
            long maxAmount,
            TransactionContext transaction
    )
    {
        long roundedAmount =
                maxAmount - Math.floorMod(maxAmount, DROPLETS_PER_FUEL);

        if (roundedAmount <= 0)
        {
            return 0;
        }

        return super.insert(
                insertedVariant,
                roundedAmount,
                transaction
        );
    }

    @Override
    protected void onFinalCommit()
    {
        owner.setHotFuel(
                (int) Math.min(
                        HearthBlockEntity.MAX_FUEL,
                        amount / DROPLETS_PER_FUEL
                )
        );
    }

    void syncFromFuel(int fuel)
    {
        int clamped = Math.max(
                0,
                Math.min(HearthBlockEntity.MAX_FUEL, fuel)
        );

        amount = (long) clamped * DROPLETS_PER_FUEL;
        variant = clamped > 0
                ? FluidVariant.of(Fluids.LAVA)
                : FluidVariant.blank();
    }
}
