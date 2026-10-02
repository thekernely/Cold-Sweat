package com.momosoftworks.coldsweat.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ColdSweatFabric implements ModInitializer
{
    public static final String MOD_ID = "cold_sweat";
    public static final Logger LOGGER = LoggerFactory.getLogger("Cold Sweat");

    @Override
    public void onInitialize()
    {
        LOGGER.info("Cold Sweat Fabric 26.2 bootstrap initialized.");
    }
}