package com.momosoftworks.coldsweat.fabric.hydration;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Custom serializers are necessary so the heat recipes can distinguish the
 * WATER potion from every other minecraft:potion stack.
 */
public final class WaterPurificationRecipes
{
    private WaterPurificationRecipes()
    {
    }

    public static void initialize()
    {
        register(
                "purified_water_bottle_smelting",
                PurifiedWaterSmeltingRecipe.SERIALIZER
        );

        register(
                "purified_water_bottle_smoking",
                PurifiedWaterSmokingRecipe.SERIALIZER
        );

        register(
                "purified_water_bottle_campfire",
                PurifiedWaterCampfireRecipe.SERIALIZER
        );

        ColdSweatFabric.LOGGER.info(
                "Registering Cold Sweat bottled-water purification recipes."
        );
    }

    private static void register(
            String path,
            RecipeSerializer<?> serializer
    )
    {
        Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                ColdSweatFabric.id(path),
                serializer
        );
    }
}
