package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.common.fluid.SlushFluid;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;

public final class ModFluids
{
    public static final FlowingFluid SLUSH = register(
            "slush",
            new SlushFluid.Source()
    );

    public static final FlowingFluid FLOWING_SLUSH = register(
            "flowing_slush",
            new SlushFluid.Flowing()
    );

    private static <T extends Fluid> T register(
            String path,
            T fluid
    )
    {
        Identifier id = ColdSweatFabric.id(path);
        ResourceKey<Fluid> key =
                ResourceKey.create(Registries.FLUID, id);

        return Registry.register(
                BuiltInRegistries.FLUID,
                key,
                fluid
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info(
                "Registering Cold Sweat Slush fluid."
        );
    }

    private ModFluids()
    {
    }
}
