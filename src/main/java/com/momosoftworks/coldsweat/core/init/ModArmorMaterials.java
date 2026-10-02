package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.data.tag.ModItemTags;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;

public final class ModArmorMaterials
{
    public static final int HOGLIN_DURABILITY = 14;
    public static final int GOAT_FUR_DURABILITY = 10;
    public static final int CHAMELEON_DURABILITY = 12;

    public static final ResourceKey<EquipmentAsset> HOGLIN_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, ColdSweatFabric.id("hoglin"));
    public static final ResourceKey<EquipmentAsset> GOAT_FUR_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, ColdSweatFabric.id("goat_fur"));
    public static final ResourceKey<EquipmentAsset> CHAMELEON_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, ColdSweatFabric.id("chameleon"));

    public static final ArmorMaterial HOGLIN = new ArmorMaterial(
            HOGLIN_DURABILITY,
            Map.of(
                    ArmorType.HELMET, 3,
                    ArmorType.CHESTPLATE, 6,
                    ArmorType.LEGGINGS, 5,
                    ArmorType.BOOTS, 2
            ),
            25,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            1.5F,
            0.0F,
            ModItemTags.HOGLIN_LEATHERS,
            HOGLIN_ASSET
    );

    public static final ArmorMaterial GOAT_FUR = new ArmorMaterial(
            GOAT_FUR_DURABILITY,
            Map.of(
                    ArmorType.HELMET, 2,
                    ArmorType.CHESTPLATE, 5,
                    ArmorType.LEGGINGS, 4,
                    ArmorType.BOOTS, 1
            ),
            15,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            0.0F,
            0.0F,
            ModItemTags.GOAT_FURS,
            GOAT_FUR_ASSET
    );

    public static final ArmorMaterial CHAMELEON = new ArmorMaterial(
            CHAMELEON_DURABILITY,
            Map.of(
                    ArmorType.HELMET, 2,
                    ArmorType.CHESTPLATE, 6,
                    ArmorType.LEGGINGS, 5,
                    ArmorType.BOOTS, 2
            ),
            15,
            ModSounds.ARMOR_EQUIP_CHAMELEON,
            0.0F,
            0.0F,
            ModItemTags.CHAMELEON_SCALES,
            CHAMELEON_ASSET
    );

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Initializing Cold Sweat armor materials.");
    }

    private ModArmorMaterials()
    {
    }
}
