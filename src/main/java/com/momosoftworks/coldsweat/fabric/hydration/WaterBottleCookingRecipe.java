package com.momosoftworks.coldsweat.fabric.hydration;

import com.momosoftworks.coldsweat.core.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Component-aware match shared by all three purification heat recipes.
 * Ingredient identity alone is insufficient because every potion uses the same
 * minecraft:potion item.
 */
public interface WaterBottleCookingRecipe
{
    default ItemStack assemblePurified()
    {
        return new ItemStack(
                ModItems.PURIFIED_WATER_BOTTLE
        );
    }

    default boolean matches(
            SingleRecipeInput recipeInput
    )
    {
        ItemStack stack =
                recipeInput.getItem(0);

        PotionContents contents =
                stack.getOrDefault(
                        DataComponents.POTION_CONTENTS,
                        PotionContents.EMPTY
                );

        return HydrationGameplayRuntime.isVanillaWaterBottle(stack)
                && contents.is(Potions.WATER);
    }
}
