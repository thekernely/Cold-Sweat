package com.momosoftworks.coldsweat.data.tag;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

public final class ModFluidTags
{
    public static final TagKey<Fluid> HOT = createTag("hot");
    public static final TagKey<Fluid> COLD = createTag("cold");
    public static final TagKey<Fluid> SLUSH = createTag("slush");

    private static TagKey<Fluid> createTag(String name)
    {
        return TagKey.create(Registries.FLUID, ColdSweatFabric.id(name));
    }

    private ModFluidTags()
    {
    }
}
