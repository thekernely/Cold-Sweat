package com.momosoftworks.coldsweat.util.registries;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * Resource keys for Cold Sweat's datapack-backed temperature damage types.
 */
public final class ModDamageSources
{
    public static final ResourceKey<DamageType> COLD =
            register("cold");

    public static final ResourceKey<DamageType> HOT =
            register("hot");

    public static boolean isFreezing(DamageSource damageSource)
    {
        return damageSource.is(COLD);
    }

    public static boolean isBurning(DamageSource damageSource)
    {
        return damageSource.is(HOT);
    }

    private static ResourceKey<DamageType> register(String name)
    {
        return ResourceKey.create(
                Registries.DAMAGE_TYPE,
                ColdSweatFabric.id(name)
        );
    }

    private ModDamageSources()
    {
    }
}
