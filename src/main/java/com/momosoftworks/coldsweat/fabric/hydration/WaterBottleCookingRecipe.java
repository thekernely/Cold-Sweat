package com.momosoftworks.coldsweat.fabric.hydration;

import com.momosoftworks.coldsweat.core.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Component-aware match/output shared by all three hydration heat recipes.
 *
 * The historical class name is kept to avoid unnecessary file churn, but this
 * now handles both:
 * - vanilla water bottles -> Cold Sweat purified water bottles
 * - reusable flasks -> same flask stack, same amount/filter state, purified
 *
 * Flask heat treatment is deliberately state-preserving:
 * it does not refill the flask and does not consume a filter charge.
 */
public interface WaterBottleCookingRecipe
{
    default ItemStack assemblePurified(
            SingleRecipeInput recipeInput
    )
    {
        ItemStack stack =
                recipeInput.getItem(0);

        if (FlaskItem.isFlask(stack))
        {
            ItemStack result =
                    stack.copyWithCount(1);

            FlaskItem.setPurified(
                    result,
                    true
            );

            return result;
        }

        return new ItemStack(
                ModItems.PURIFIED_WATER_BOTTLE
        );
    }

    default boolean matchesHydrationContainer(
            SingleRecipeInput recipeInput
    )
    {
        ItemStack stack =
                recipeInput.getItem(0);

        if (FlaskItem.isFlask(stack))
        {
            return FlaskItem.waterAmount(stack) > 0
                    && !FlaskItem.isPurified(stack);
        }

        PotionContents contents =
                stack.getOrDefault(
                        DataComponents.POTION_CONTENTS,
                        PotionContents.EMPTY
                );

        return HydrationGameplayRuntime.isVanillaWaterBottle(stack)
                && contents.is(Potions.WATER);
    }
}
