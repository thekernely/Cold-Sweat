package com.momosoftworks.coldsweat.data.tag;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public final class ModBiomeTags
{
    /**
     * Fabric/Common counterpart to NeoForge Tags.Biomes.IS_UNDERGROUND.
     *
     * NeoForge defines this as c:is_underground. We keep the same key so other
     * mods/datapacks can contribute cave biomes without a Cold Sweat-specific
     * compatibility hook.
     */
    public static final TagKey<Biome> IS_UNDERGROUND =
            createCommonTag("is_underground");

    private static TagKey<Biome> createTag(String name)
    {
        return TagKey.create(
                Registries.BIOME,
                ColdSweatFabric.id(name)
        );
    }

    private static TagKey<Biome> createCommonTag(String name)
    {
        return TagKey.create(
                Registries.BIOME,
                Identifier.fromNamespaceAndPath("c", name)
        );
    }

    private ModBiomeTags()
    {
    }
}
