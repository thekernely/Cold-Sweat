package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.api.registry.TempModifierRegistry;
import com.momosoftworks.coldsweat.api.temperature.modifier.BiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.ElevationTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.ShadeTempModifier;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;

/**
 * Fabric-native temperature-modifier registry bootstrap.
 *
 * Only modifiers that have actually been ported are registered here. The IDs
 * match upstream Cold Sweat so serialized/config/API references remain stable
 * as later modifiers are restored.
 */
public final class TempModifierInit
{
    public static void initialize()
    {
        TempModifierRegistry.flush();

        TempModifierRegistry.register(
                ColdSweatFabric.id("biomes"),
                BiomeTempModifier::new
        );
        TempModifierRegistry.register(
                ColdSweatFabric.id("shade"),
                ShadeTempModifier::new
        );
        TempModifierRegistry.register(
                ColdSweatFabric.id("elevation"),
                ElevationTempModifier::new
        );

        ColdSweatFabric.LOGGER.info(
                "Registered {} Cold Sweat temperature modifier type(s).",
                TempModifierRegistry.getEntries().size()
        );
    }

    private TempModifierInit()
    {
    }
}
