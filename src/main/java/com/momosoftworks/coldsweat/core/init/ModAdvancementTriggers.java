package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.core.advancement.trigger.ArmorInsulatedTrigger;
import com.momosoftworks.coldsweat.core.advancement.trigger.SoulLampFueledTrigger;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModAdvancementTriggers
{
    public static final SoulLampFueledTrigger SOUL_LAMP_FUELED =
            register("soulspring_lamp_fueled", new SoulLampFueledTrigger());

    public static final ArmorInsulatedTrigger ARMOR_INSULATED =
            register("armor_insulated", new ArmorInsulatedTrigger());

    private static <T extends CriterionTrigger<?>> T register(String path, T trigger)
    {
        return Registry.register(
                BuiltInRegistries.TRIGGER_TYPES,
                ColdSweatFabric.id(path),
                trigger
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat standalone advancement triggers.");
    }

    private ModAdvancementTriggers()
    {
    }
}
