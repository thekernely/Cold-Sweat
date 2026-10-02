package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModParticleTypes
{
    public static final SimpleParticleType WARM_AIR = register("warm_air");
    public static final SimpleParticleType COLD_AIR = register("cold_air");

    public static final SimpleParticleType SMOKESTACK_WARM = register("smokestack_warm");
    public static final SimpleParticleType SMOKESTACK_COLD = register("smokestack_cold");

    public static final SimpleParticleType GROUND_MIST = register("ground_mist");

    public static final SimpleParticleType MOB_COLD = register("mob_cold");
    public static final SimpleParticleType MOB_HOT = register("mob_hot");

    private static SimpleParticleType register(String path)
    {
        return Registry.register(
                BuiltInRegistries.PARTICLE_TYPE,
                ColdSweatFabric.id(path),
                FabricParticleTypes.simple()
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat particle types.");
    }

    private ModParticleTypes()
    {
    }
}
