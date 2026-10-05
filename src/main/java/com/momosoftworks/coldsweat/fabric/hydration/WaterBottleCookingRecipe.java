package com.momosoftworks.coldsweat.fabric.hydration;

import com.momosoftworks.coldsweat.common.item.FilledWaterskinItem;
import com.momosoftworks.coldsweat.core.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Component-aware match/output shared by all three hydration heat recipes.
 *
 * Heat treatment now handles:
 * - vanilla water bottles -> Cold Sweat purified water bottles
 * - reusable flasks -> same flask stack, same amount/filter state, purified
 * - filled Waterskins -> same one-use Waterskin, Very Warm + Purified
 *
 * Flask/Waterskin heat treatment is state-preserving: it does not refill a
 * container and does not consume a Flask filter charge.
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

        if (stack.is(ModItems.FILLED_WATERSKIN))
        {
            ItemStack result =
                    stack.copyWithCount(1);

            FilledWaterskinItem.heatAndPurify(
                    result
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

        if (stack.is(ModItems.FILLED_WATERSKIN))
        {
            return !FilledWaterskinItem.isPurified(stack)
                    || !FilledWaterskinItem.isVeryWarm(stack);
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
