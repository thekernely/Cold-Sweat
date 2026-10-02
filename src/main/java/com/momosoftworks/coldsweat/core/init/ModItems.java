package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;

public final class ModItems
{
    public static final Item GOAT_FUR = registerSimple("goat_fur");
    public static final Item HOGLIN_HIDE = registerSimple("hoglin_hide");
    public static final Item CHAMELEON_MOLT = registerSimple("chameleon_molt");

    /*
     * Minecraft 26.2 defines humanoid armor through item components rather than
     * the old ArmorItem subclass constructor. These registrations preserve the
     * upstream materials, armor slots, and durability values while leaving
     * custom client models for the M7 rendering milestone.
     */
    public static final Item HOGLIN_HELMET = registerArmor(
            "hoglin_helmet", ModArmorMaterials.HOGLIN, ArmorType.HELMET, ModArmorMaterials.HOGLIN_DURABILITY
    );
    public static final Item HOGLIN_CHESTPLATE = registerArmor(
            "hoglin_chestplate", ModArmorMaterials.HOGLIN, ArmorType.CHESTPLATE, ModArmorMaterials.HOGLIN_DURABILITY
    );
    public static final Item HOGLIN_LEGGINGS = registerArmor(
            "hoglin_leggings", ModArmorMaterials.HOGLIN, ArmorType.LEGGINGS, ModArmorMaterials.HOGLIN_DURABILITY
    );
    public static final Item HOGLIN_BOOTS = registerArmor(
            "hoglin_boots", ModArmorMaterials.HOGLIN, ArmorType.BOOTS, ModArmorMaterials.HOGLIN_DURABILITY
    );

    public static final Item GOAT_FUR_HELMET = registerArmor(
            "goat_fur_helmet", ModArmorMaterials.GOAT_FUR, ArmorType.HELMET, ModArmorMaterials.GOAT_FUR_DURABILITY
    );
    public static final Item GOAT_FUR_CHESTPLATE = registerArmor(
            "goat_fur_chestplate", ModArmorMaterials.GOAT_FUR, ArmorType.CHESTPLATE, ModArmorMaterials.GOAT_FUR_DURABILITY
    );
    public static final Item GOAT_FUR_LEGGINGS = registerArmor(
            "goat_fur_leggings", ModArmorMaterials.GOAT_FUR, ArmorType.LEGGINGS, ModArmorMaterials.GOAT_FUR_DURABILITY
    );
    public static final Item GOAT_FUR_BOOTS = registerArmor(
            "goat_fur_boots", ModArmorMaterials.GOAT_FUR, ArmorType.BOOTS, ModArmorMaterials.GOAT_FUR_DURABILITY
    );

    public static final Item CHAMELEON_HELMET = registerArmor(
            "chameleon_helmet", ModArmorMaterials.CHAMELEON, ArmorType.HELMET, ModArmorMaterials.CHAMELEON_DURABILITY
    );
    public static final Item CHAMELEON_CHESTPLATE = registerArmor(
            "chameleon_chestplate", ModArmorMaterials.CHAMELEON, ArmorType.CHESTPLATE, ModArmorMaterials.CHAMELEON_DURABILITY
    );
    public static final Item CHAMELEON_LEGGINGS = registerArmor(
            "chameleon_leggings", ModArmorMaterials.CHAMELEON, ArmorType.LEGGINGS, ModArmorMaterials.CHAMELEON_DURABILITY
    );
    public static final Item CHAMELEON_BOOTS = registerArmor(
            "chameleon_boots", ModArmorMaterials.CHAMELEON, ArmorType.BOOTS, ModArmorMaterials.CHAMELEON_DURABILITY
    );

    private static Item registerSimple(String path)
    {
        return register(path, new Item.Properties());
    }

    private static Item registerArmor(
            String path,
            net.minecraft.world.item.equipment.ArmorMaterial material,
            ArmorType type,
            int durabilityMultiplier
    )
    {
        return register(
                path,
                new Item.Properties()
                        .humanoidArmor(material, type)
                        .durability(type.getDurability(durabilityMultiplier))
        );
    }

    private static Item register(String path, Item.Properties properties)
    {
        Identifier id = ColdSweatFabric.id(path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);

        return Registry.register(
                BuiltInRegistries.ITEM,
                key,
                new Item(properties.setId(key))
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat material and armor items.");
    }

    private ModItems()
    {
    }
}
