package com.momosoftworks.coldsweat.data.tag;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class ModEntityTags
{
    // Entities in this tag need more accurate temperature information.
    public static final TagKey<EntityType<?>> TEMPERATURE_SENSITIVE = createTag("temperature_sensitive");
    public static final TagKey<EntityType<?>> CHAMELEON_EATS = createTag("chameleon_eats");

    private static TagKey<EntityType<?>> createTag(String name)
    {
        return TagKey.create(Registries.ENTITY_TYPE, ColdSweatFabric.id(name));
    }

    private ModEntityTags()
    {
    }
}
