package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems
{
    /*
     * Start the item registry with the three dependency-free material items.
     * Gameplay-heavy items (waterskins, thermometer, lamp, armor, block items,
     * spawn eggs, etc.) are added in later slices alongside their dependencies.
     */
    public static final Item GOAT_FUR = registerSimple("goat_fur");
    public static final Item HOGLIN_HIDE = registerSimple("hoglin_hide");
    public static final Item CHAMELEON_MOLT = registerSimple("chameleon_molt");

    private static Item registerSimple(String path)
    {
        Identifier id = ColdSweatFabric.id(path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);

        return Registry.register(
                BuiltInRegistries.ITEM,
                key,
                new Item(new Item.Properties().setId(key))
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat simple items.");
    }

    private ModItems()
    {
    }
}
