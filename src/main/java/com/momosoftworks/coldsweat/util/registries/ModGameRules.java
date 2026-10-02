package com.momosoftworks.coldsweat.util.registries;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public final class ModGameRules
{
    public static final GameRule<Boolean> RULE_SLUSH_SOURCE_CONVERSION = GameRuleBuilder
            .forBoolean(false)
            .category(GameRuleCategory.MISC)
            .buildAndRegister(ColdSweatFabric.id("slush_source_conversion"));

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat game rules.");
    }

    private ModGameRules()
    {
    }
}
