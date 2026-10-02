package com.momosoftworks.coldsweat.api.registry;

import com.momosoftworks.coldsweat.api.insulation.AdaptiveInsulation;
import com.momosoftworks.coldsweat.api.insulation.Insulation;
import com.momosoftworks.coldsweat.api.insulation.StaticInsulation;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader-independent runtime registry for built-in armor insulation.
 *
 * Upstream ultimately fills this information through InsulatorData/config data.
 * That complete requirement/config graph is intentionally not pulled forward
 * just to restore armor behavior. This registry is the Fabric boundary that can
 * later be populated by the full config loader without changing the runtime.
 */
public final class InsulationRegistry
{
    private static final Map<Item, List<Insulation>> ARMOR_INSULATION =
            new IdentityHashMap<>();

    static
    {
        // Upstream ItemSettingsConfig defaults - vanilla leather armor.
        registerArmor(Items.LEATHER_HELMET, new StaticInsulation(5.0, 5.0));
        registerArmor(Items.LEATHER_CHESTPLATE, new StaticInsulation(7.0, 7.0));
        registerArmor(Items.LEATHER_LEGGINGS, new StaticInsulation(6.0, 6.0));
        registerArmor(Items.LEATHER_BOOTS, new StaticInsulation(5.0, 5.0));

        // Upstream Cold Sweat armor defaults.
        registerArmor(ModItems.HOGLIN_HELMET, new StaticInsulation(0.0, 10.0));
        registerArmor(ModItems.HOGLIN_CHESTPLATE, new StaticInsulation(0.0, 14.0));
        registerArmor(ModItems.HOGLIN_LEGGINGS, new StaticInsulation(0.0, 12.0));
        registerArmor(ModItems.HOGLIN_BOOTS, new StaticInsulation(0.0, 10.0));

        registerArmor(ModItems.GOAT_FUR_HELMET, new StaticInsulation(10.0, 0.0));
        registerArmor(ModItems.GOAT_FUR_CHESTPLATE, new StaticInsulation(14.0, 0.0));
        registerArmor(ModItems.GOAT_FUR_LEGGINGS, new StaticInsulation(12.0, 0.0));
        registerArmor(ModItems.GOAT_FUR_BOOTS, new StaticInsulation(10.0, 0.0));

        registerArmor(ModItems.CHAMELEON_HELMET, new AdaptiveInsulation(10.0, 0.0085));
        registerArmor(ModItems.CHAMELEON_CHESTPLATE, new AdaptiveInsulation(14.0, 0.0085));
        registerArmor(ModItems.CHAMELEON_LEGGINGS, new AdaptiveInsulation(12.0, 0.0085));
        registerArmor(ModItems.CHAMELEON_BOOTS, new AdaptiveInsulation(10.0, 0.0085));
    }

    public static void registerArmor(Item item, Insulation... insulation)
    {
        ARMOR_INSULATION.put(item, List.of(insulation));
    }

    public static void registerArmor(Item item, List<? extends Insulation> insulation)
    {
        ARMOR_INSULATION.put(item, List.copyOf(insulation));
    }

    public static List<Insulation> getArmorInsulation(ItemStack stack)
    {
        List<Insulation> insulation = ARMOR_INSULATION.get(stack.getItem());
        return insulation == null ? List.of() : Insulation.deepCopy(insulation);
    }

    public static boolean hasArmorInsulation(ItemStack stack)
    {
        return ARMOR_INSULATION.containsKey(stack.getItem());
    }

    public static void initialize()
    {
        ColdSweatFabric.LOGGER.info(
                "Initialized {} built-in armor insulation definition(s).",
                ARMOR_INSULATION.size()
        );
    }

    private InsulationRegistry()
    {
    }
}
