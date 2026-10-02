package com.momosoftworks.coldsweat.common.blockentity;

import com.momosoftworks.coldsweat.core.init.ModFluids;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Insertion-only Fabric Transfer API view of one Cold Sweat fuel tank.
 *
 * One bucket is exactly 1000 fuel points, preserving the upstream machine
 * capacity while keeping Fabric's droplet units transactional.
 */
public final class ThermalFluidStorage extends SingleFluidStorage
{
    private static final long DROPLETS_PER_FUEL =
            FluidConstants.BUCKET / HearthBlockEntity.MAX_FUEL;

    private final HearthBlockEntity owner;
    private final FuelKind kind;

    public ThermalFluidStorage(
            HearthBlockEntity owner,
            FuelKind kind
    )
    {
        this.owner = owner;
        this.kind = kind;
        syncFromFuel(currentFuel());
    }

    @Override
    protected long getCapacity(FluidVariant variant)
    {
        return FluidConstants.BUCKET;
    }

    @Override
    protected boolean canInsert(FluidVariant variant)
    {
        return variant.getFluid() == acceptedFluid();
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
                maxAmount - Math.floorMod(
                        maxAmount,
                        DROPLETS_PER_FUEL
                );

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
        int fuel = (int) Math.min(
                HearthBlockEntity.MAX_FUEL,
                amount / DROPLETS_PER_FUEL
        );

        if (kind == FuelKind.HOT)
        {
            owner.setHotFuel(fuel);
        }
        else
        {
            owner.setColdFuel(fuel);
        }
    }

    void syncFromFuel(int fuel)
    {
        int clamped = Math.max(
                0,
                Math.min(
                        HearthBlockEntity.MAX_FUEL,
                        fuel
                )
        );

        amount = (long) clamped * DROPLETS_PER_FUEL;
        variant = clamped > 0
                ? FluidVariant.of(acceptedFluid())
                : FluidVariant.blank();
    }

    private int currentFuel()
    {
        return kind == FuelKind.HOT
                ? owner.getHotFuel()
                : owner.getColdFuel();
    }

    private Fluid acceptedFluid()
    {
        return kind == FuelKind.HOT
                ? Fluids.LAVA
                : ModFluids.SLUSH;
    }

    public enum FuelKind
    {
        HOT,
        COLD
    }
}
