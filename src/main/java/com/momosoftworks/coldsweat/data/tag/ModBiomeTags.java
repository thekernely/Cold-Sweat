package com.momosoftworks.coldsweat.data.tag;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public final class ModBiomeTags
{
    private static TagKey<Biome> createTag(String name)
    {
        return TagKey.create(Registries.BIOME, ColdSweatFabric.id(name));
    }

    private static TagKey<Biome> createCommonTag(String name)
    {
        return TagKey.create(Registries.BIOME, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", name));
    }

    private ModBiomeTags()
    {
    }
}
