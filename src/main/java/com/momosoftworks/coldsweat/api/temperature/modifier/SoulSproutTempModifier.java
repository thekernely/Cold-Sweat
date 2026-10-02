package com.momosoftworks.coldsweat.api.temperature.modifier;

import net.minecraft.world.item.Item;

/**
 * Distinct modifier type for Soul Sprout food temperature.
 *
 * Upstream also emits client soul particles while this modifier is active.
 * Those particles stay with the M7 client-visual milestone; the gameplay
 * temperature effect is restored here.
 */
public final class SoulSproutTempModifier extends FoodTempModifier
{
    public SoulSproutTempModifier(Item source, double temperature)
    {
        super(source, temperature);
    }
}
