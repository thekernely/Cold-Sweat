package com.momosoftworks.coldsweat.data.tag;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;

public final class ModEffectTags
{
    public static final TagKey<MobEffect> HEARTH_BLACKLISTED = createTag("hearth_blacklisted");

    private static TagKey<MobEffect> createTag(String name)
    {
        return TagKey.create(Registries.MOB_EFFECT, ColdSweatFabric.id(name));
    }

    private ModEffectTags()
    {
    }
}
