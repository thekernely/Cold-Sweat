package com.momosoftworks.coldsweat.fabric;

import com.momosoftworks.coldsweat.api.registry.InsulationRegistry;
import com.momosoftworks.coldsweat.api.registry.ItemTemperatureRegistry;
import com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager;
import com.momosoftworks.coldsweat.common.capability.handler.PlayerHydrationManager;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureModifierRuntime;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureDamageRuntime;
import com.momosoftworks.coldsweat.core.init.BlockTempInit;
import com.momosoftworks.coldsweat.core.init.ModAdvancementTriggers;
import com.momosoftworks.coldsweat.core.init.ModArmorMaterials;
import com.momosoftworks.coldsweat.core.init.ModAttributes;
import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import com.momosoftworks.coldsweat.core.init.ModBlocks;
import com.momosoftworks.coldsweat.core.init.ModCreativeTabs;
import com.momosoftworks.coldsweat.core.init.ModDataAttachments;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.core.init.ModFluids;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import com.momosoftworks.coldsweat.core.init.MachineBlockTempInit;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.core.init.ModMenus;
import com.momosoftworks.coldsweat.core.init.ModParticleTypes;
import com.momosoftworks.coldsweat.core.init.ModPotions;
import com.momosoftworks.coldsweat.core.init.ModSounds;
import com.momosoftworks.coldsweat.core.init.TempModifierInit;
import com.momosoftworks.coldsweat.fabric.hydration.FoodHydrationRegistry;
import com.momosoftworks.coldsweat.fabric.hydration.HydrationGameplayRuntime;
import com.momosoftworks.coldsweat.fabric.season.SeasonContextService;
import com.momosoftworks.coldsweat.util.registries.ModGameRules;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ColdSweatFabric implements ModInitializer
{
    public static final String MOD_ID = "cold_sweat";
    public static final Logger LOGGER = LoggerFactory.getLogger("Cold Sweat");

    public static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize()
    {
        LOGGER.info("Cold Sweat Fabric 26.2 bootstrap initialized.");

        ModSounds.initialize();
        ModParticleTypes.initialize();
        ModAttributes.initialize();
        ModEffects.initialize();
        ModPotions.initialize();
        ModArmorMaterials.initialize();
        ModItemComponents.initialize();
        ModFluids.initialize();
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();
        ModMenus.initialize();
        InsulationRegistry.initialize();
        ItemTemperatureRegistry.initialize();
        ModAdvancementTriggers.initialize();
        ModCreativeTabs.initialize();
        ModDataAttachments.initialize();
        EntityTempManager.initialize();
        PlayerHydrationManager.initialize();
        HydrationGameplayRuntime.initialize();
        FoodHydrationRegistry.initialize();
        TempModifierInit.initialize();
        BlockTempInit.initialize();
        MachineBlockTempInit.initialize();
        TemperatureModifierRuntime.initialize();
        TemperatureDamageRuntime.initialize();
        ModGameRules.initialize();
        SeasonContextService.initialize();
    }
}
