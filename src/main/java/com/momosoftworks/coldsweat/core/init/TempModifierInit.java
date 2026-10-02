package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.api.registry.TempModifierRegistry;
import com.momosoftworks.coldsweat.api.temperature.modifier.ArmorInsulationTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.BiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.CaveBiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.BlockTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.ElevationTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.EntitiesTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.FreezingTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.ShadeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.WaterTempModifier;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;

/**
 * Fabric-native temperature-modifier registry bootstrap.
 */
public final class TempModifierInit
{
    public static void initialize()
    {
        TempModifierRegistry.flush();

        TempModifierRegistry.register(
                ColdSweatFabric.id("blocks"),
                BlockTempModifier::new
        );
        TempModifierRegistry.register(
                ColdSweatFabric.id("entities"),
                EntitiesTempModifier::new
        );
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
        TempModifierRegistry.register(
                ColdSweatFabric.id("cave_biomes"),
                CaveBiomeTempModifier::new
        );
        TempModifierRegistry.register(
                ColdSweatFabric.id("armor"),
                ArmorInsulationTempModifier::new
        );
        TempModifierRegistry.register(
                ColdSweatFabric.id("water"),
                WaterTempModifier::new
        );
        TempModifierRegistry.register(
                ColdSweatFabric.id("freezing"),
                FreezingTempModifier::new
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
