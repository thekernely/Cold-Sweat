package com.momosoftworks.coldsweat.fabric;

import com.momosoftworks.coldsweat.core.init.ModAttributes;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.core.init.ModParticleTypes;
import com.momosoftworks.coldsweat.core.init.ModPotions;
import com.momosoftworks.coldsweat.core.init.ModSounds;
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
        ModGameRules.initialize();
    }
}
