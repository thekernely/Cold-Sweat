package com.momosoftworks.coldsweat.common.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Raw-water contamination marker.
 *
 * The effect itself is intentionally stateless. M8's hydration runtime owns
 * the actual exhaustion acceleration so all water loss continues through the
 * same saturation/exhaustion pipeline.
 */
public final class ThirstEffect extends MobEffect
{
    public ThirstEffect()
    {
        super(
                MobEffectCategory.HARMFUL,
                0x718A39
        );
    }
}
