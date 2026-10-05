package com.momosoftworks.coldsweat.fabric.hydration;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmokingRecipe;

public final class PurifiedWaterSmokingRecipe
        extends SmokingRecipe
        implements WaterBottleCookingRecipe
{
    public static final MapCodec<PurifiedWaterSmokingRecipe> MAP_CODEC =
            cookingMapCodec(
                    PurifiedWaterSmokingRecipe::new,
                    100
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PurifiedWaterSmokingRecipe> STREAM_CODEC =
            cookingStreamCodec(
                    PurifiedWaterSmokingRecipe::new
            );

    public static final RecipeSerializer<PurifiedWaterSmokingRecipe> SERIALIZER =
            new RecipeSerializer<>(
                    MAP_CODEC,
                    STREAM_CODEC
            );

    public PurifiedWaterSmokingRecipe(
            Recipe.CommonInfo commonInfo,
            AbstractCookingRecipe.CookingBookInfo bookInfo,
            Ingredient ingredient,
            ItemStackTemplate result,
            float experience,
            int cookingTime
    )
    {
        super(
                commonInfo,
                bookInfo,
                ingredient,
                result,
                experience,
                cookingTime
        );
    }

    @Override
    public ItemStack assemble(
            SingleRecipeInput recipeInput
    )
    {
        return assemblePurified(
                recipeInput
        );
    }

    @Override
    public boolean matches(
            SingleRecipeInput recipeInput,
            Level level
    )
    {
        return matchesHydrationContainer(
                recipeInput
        );
    }

    @Override
    public boolean isSpecial()
    {
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public RecipeSerializer<SmokingRecipe> getSerializer()
    {
        return (RecipeSerializer) SERIALIZER;
    }
}
