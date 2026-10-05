package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTabs
{
    public static final ResourceKey<CreativeModeTab> COLD_SWEAT_TAB_KEY = ResourceKey.create(
            BuiltInRegistries.CREATIVE_MODE_TAB.key(),
            ColdSweatFabric.id("cold_sweat")
    );

    public static final CreativeModeTab COLD_SWEAT_TAB = FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.cold_sweat"))
            .icon(() -> new ItemStack(ModItems.CHAMELEON_MOLT))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.GOAT_FUR);
                output.accept(ModItems.HOGLIN_HIDE);
                output.accept(ModItems.CHAMELEON_MOLT);
                output.accept(ModItems.SOUL_SPROUT);
                output.accept(ModItems.WATERSKIN);
                output.accept(ModItems.FILLED_WATERSKIN);
                output.accept(ModItems.PURIFIED_WATER_BOTTLE);
                output.accept(ModItems.WATER_FILTER);
                output.accept(ModItems.LEATHER_FLASK);
                output.accept(ModItems.COPPER_FLASK);
                output.accept(ModItems.IRON_FLASK);
                output.accept(ModItems.GOLD_FLASK);
                output.accept(ModItems.DIAMOND_FLASK);
                output.accept(ModItems.NETHERITE_FLASK);
                output.accept(ModItems.THERMOMETER);

                output.accept(ModItems.HOGLIN_HELMET);
                output.accept(ModItems.HOGLIN_CHESTPLATE);
                output.accept(ModItems.HOGLIN_LEGGINGS);
                output.accept(ModItems.HOGLIN_BOOTS);

                output.accept(ModItems.GOAT_FUR_HELMET);
                output.accept(ModItems.GOAT_FUR_CHESTPLATE);
                output.accept(ModItems.GOAT_FUR_LEGGINGS);
                output.accept(ModItems.GOAT_FUR_BOOTS);

                output.accept(ModItems.CHAMELEON_HELMET);
                output.accept(ModItems.CHAMELEON_CHESTPLATE);
                output.accept(ModItems.CHAMELEON_LEGGINGS);
                output.accept(ModItems.CHAMELEON_BOOTS);
            })
            .build();

    public static void initialize()
    {
        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                COLD_SWEAT_TAB_KEY,
                COLD_SWEAT_TAB
        );
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat creative tab items.");
    }

    private ModCreativeTabs()
    {
    }
}
