package com.momosoftworks.coldsweat.core.init;

import com.momosoftworks.coldsweat.common.item.FilledWaterskinItem;
import com.momosoftworks.coldsweat.common.item.SoulSproutItem;
import com.momosoftworks.coldsweat.common.item.ThermometerItem;
import com.momosoftworks.coldsweat.common.item.WaterskinItem;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import com.momosoftworks.coldsweat.fabric.hydration.PurifiedWaterBottleItem;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.function.Function;

public final class ModItems
{
    public static final Item GOAT_FUR = registerSimple("goat_fur");
    public static final Item HOGLIN_HIDE = registerSimple("hoglin_hide");
    public static final Item CHAMELEON_MOLT = registerSimple("chameleon_molt");

    public static final Item SOUL_SPROUT = registerSoulSprout();

    public static final Item WATERSKIN = register(
            "waterskin",
            WaterskinItem::new,
            new Item.Properties().stacksTo(16)
    );

    public static final Item FILLED_WATERSKIN = register(
            "filled_waterskin",
            FilledWaterskinItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .craftRemainder(WATERSKIN)
                    .component(ModItemComponents.WATER_TEMPERATURE, 0.0)
                    .component(
                            DataComponents.CONSUMABLE,
                            Consumable.builder()
                                    .consumeSeconds(1.6F)
                                    .animation(ItemUseAnimation.DRINK)
                                    .sound(SoundEvents.GENERIC_DRINK)
                                    .hasConsumeParticles(false)
                                    .build()
                    )
    );

    public static final Item THERMOMETER = register(
            "thermometer",
            ThermometerItem::new,
            new Item.Properties()
                    .rarity(Rarity.UNCOMMON)
                    .stacksTo(1)
    );

    /*
     * M8.4: ordinary minecraft:water_bottle remains the raw-water bottle.
     * Purified water is the only additional bottle item because it represents
     * a genuinely new water-quality state rather than replacing vanilla
     * functionality.
     */
    public static final Item PURIFIED_WATER_BOTTLE = register(
            "purified_water_bottle",
            PurifiedWaterBottleItem::new,
            new Item.Properties()
                    .stacksTo(16)
                    .craftRemainder(Items.GLASS_BOTTLE)
                    .component(
                            DataComponents.CONSUMABLE,
                            Consumables.DEFAULT_DRINK
                    )
                    .usingConvertsTo(Items.GLASS_BOTTLE)
    );

    // M6 thermal-machine block items. Hearth intentionally maps to hearth_bottom.
    public static final Item HEARTH = registerBlockItem(
            "hearth", ModBlocks.HEARTH_BOTTOM, new Item.Properties().stacksTo(1)
    );
    public static final Item BOILER = registerBlockItem(
            "boiler", ModBlocks.BOILER, new Item.Properties()
    );
    public static final Item ICEBOX = registerBlockItem(
            "icebox", ModBlocks.ICEBOX, new Item.Properties()
    );

    public static final Item SMOKESTACK = registerBlockItem(
            "smokestack", ModBlocks.SMOKESTACK, new Item.Properties()
    );


    public static final Item SLUSH_BUCKET = register(
            "slush_bucket",
            properties -> new BucketItem(
                    ModFluids.SLUSH,
                    properties
            ),
            new Item.Properties()
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1)
    );

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

    private static Item registerSoulSprout()
    {
        FoodProperties food = new FoodProperties.Builder()
                .nutrition(3)
                .saturationModifier(0.5f)
                .alwaysEdible()
                .build();

        return register(
                "soul_sprout",
                SoulSproutItem::new,
                new Item.Properties().food(
                        food,
                        Consumables.defaultFood()
                                .consumeSeconds(0.8f)
                                .build()
                )
        );
    }

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
        return register(path, Item::new, properties);
    }

    private static Item registerBlockItem(String path, net.minecraft.world.level.block.Block block, Item.Properties properties)
    {
        return register(path, props -> new BlockItem(block, props.useBlockDescriptionPrefix()), properties);
    }

    private static Item register(
            String path,
            Function<Item.Properties, ? extends Item> factory,
            Item.Properties properties
    )
    {
        Identifier id = ColdSweatFabric.id(path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);

        return Registry.register(
                BuiltInRegistries.ITEM,
                key,
                factory.apply(properties.setId(key))
        );
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info("Registering Cold Sweat material, hydration, consumable, utility, armor, thermal-machine, and Slush items.");
    }

    private ModItems()
    {
    }
}
