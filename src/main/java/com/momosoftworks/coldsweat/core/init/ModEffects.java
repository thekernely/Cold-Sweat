package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.common.effect.FrigidnessEffect;
import com.momosoftworks.coldsweat.common.effect.GraceEffect;
import com.momosoftworks.coldsweat.common.effect.IceResistanceEffect;
import com.momosoftworks.coldsweat.common.effect.ThirstEffect;
import com.momosoftworks.coldsweat.common.effect.WarmthEffect;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

public final class ModEffects
{
    public static final Holder<MobEffect> FRIGIDNESS = register("frigidness", new FrigidnessEffect());
    public static final Holder<MobEffect> WARMTH = register("warmth", new WarmthEffect());
    public static final Holder<MobEffect> GRACE = register("grace", new GraceEffect());
    public static final Holder<MobEffect> ICE_RESISTANCE = register("ice_resistance", new IceResistanceEffect());
    public static final Holder<MobEffect> THIRST = register("thirst", new ThirstEffect());

    private static Holder<MobEffect> register(String path, MobEffect effect)
    {
        return Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                ColdSweatFabric.id(path),
                effect
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat mob effects.");
    }

    private ModEffects()
    {
    }
}
