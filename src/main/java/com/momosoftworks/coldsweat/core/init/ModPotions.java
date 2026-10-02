package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

public final class ModPotions
{
    public static final Holder<Potion> ICE_RESISTANCE = register(
            "ice_resistance",
            new Potion(
                    "ice_resistance",
                    new MobEffectInstance(ModEffects.ICE_RESISTANCE, 3600)
            )
    );

    public static final Holder<Potion> LONG_ICE_RESISTANCE = register(
            "long_ice_resistance",
            new Potion(
                    "ice_resistance",
                    new MobEffectInstance(ModEffects.ICE_RESISTANCE, 7200)
            )
    );

    private static Holder<Potion> register(String path, Potion potion)
    {
        return Registry.registerForHolder(
                BuiltInRegistries.POTION,
                ColdSweatFabric.id(path),
                potion
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat potions.");
    }

    private ModPotions()
    {
    }
}
