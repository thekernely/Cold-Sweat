package com.momosoftworks.coldsweat.common.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Visible physiological state used by heated Waterskins.
 *
 * Gameplay is owned by ItemTemperatureRegistry so the effect remains a small,
 * explicit piece of player-facing state rather than a second temperature
 * simulation.
 */
public final class WarmingEffect extends MobEffect
{
    public WarmingEffect()
    {
        super(MobEffectCategory.BENEFICIAL, 0xE89A3C);
    }
}
