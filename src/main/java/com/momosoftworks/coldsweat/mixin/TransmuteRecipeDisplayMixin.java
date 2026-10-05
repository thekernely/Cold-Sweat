package com.momosoftworks.coldsweat.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * M8.10 cleanup: vanilla transmute recipes preserve components correctly, so
 * flask upgrades intentionally use crafting_transmute. Minecraft's generic
 * recipe-book display, however, visually collapses the material-count form to
 * one flask + one material even when the recipe requires eight materials.
 *
 * Keep the real recipe/transmutation semantics untouched; only replace the
 * recipe-book presentation for the Cold Sweat flask-upgrade group with the
 * familiar 3x3 "8 materials around the previous flask" layout.
 */
@Mixin(TransmuteRecipe.class)
public abstract class TransmuteRecipeDisplayMixin
{
    private static final String COLD_SWEAT_FLASK_UPGRADE_GROUP =
            "cold_sweat_flask_upgrade";

    @Shadow
    @Final
    private Ingredient input;

    @Shadow
    @Final
    private Ingredient material;

    @Inject(
            method = "display",
            at = @At("RETURN"),
            cancellable = true
    )
    private void coldSweat$showEightMaterialFlaskUpgrade(
            CallbackInfoReturnable<List<RecipeDisplay>> cir
    )
    {
        if (!COLD_SWEAT_FLASK_UPGRADE_GROUP.equals(
                ((TransmuteRecipe) (Object) this).group()
        ))
        {
            return;
        }

        List<RecipeDisplay> vanillaDisplays =
                cir.getReturnValue();

        if (vanillaDisplays == null
                || vanillaDisplays.isEmpty()
                || !(vanillaDisplays.get(0)
                instanceof ShapelessCraftingRecipeDisplay vanillaDisplay))
        {
            return;
        }

        SlotDisplay materialDisplay =
                this.material.display();

        SlotDisplay inputDisplay =
                this.input.display();

        List<SlotDisplay> ingredients =
                List.of(
                        materialDisplay,
                        materialDisplay,
                        materialDisplay,
                        materialDisplay,
                        inputDisplay,
                        materialDisplay,
                        materialDisplay,
                        materialDisplay,
                        materialDisplay
                );

        cir.setReturnValue(
                List.of(
                        new ShapedCraftingRecipeDisplay(
                                3,
                                3,
                                ingredients,
                                vanillaDisplay.result(),
                                vanillaDisplay.craftingStation()
                        )
                )
        );
    }
}
