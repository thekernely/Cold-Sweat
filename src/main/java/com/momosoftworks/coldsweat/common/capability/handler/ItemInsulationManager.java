package com.momosoftworks.coldsweat.common.capability.handler;

import com.momosoftworks.coldsweat.api.registry.InsulationRegistry;
import com.momosoftworks.coldsweat.common.capability.insulation.ItemInsulationCap;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Optional;

/**
 * Fabric-side sewn-insulation component manager.
 *
 * This restores the core upstream semantics needed before the Sewing Table UI:
 * armor eligibility, static slot capacity, persistent component state, add and
 * remove operations, and resolved built-in insulation ingredients.
 */
public final class ItemInsulationManager
{
    public static Optional<ItemInsulationCap> getInsulationCap(ItemStack stack)
    {
        if (!isInsulatable(stack))
        {
            return Optional.empty();
        }

        ItemInsulationCap cap = stack.get(ModItemComponents.ARMOR_INSULATION);
        if (cap == null)
        {
            cap = new ItemInsulationCap();
            stack.set(ModItemComponents.ARMOR_INSULATION, cap);
        }
        return Optional.of(cap);
    }

    public static Optional<ItemInsulationCap> getExistingInsulationCap(ItemStack stack)
    {
        return Optional.ofNullable(stack.get(ModItemComponents.ARMOR_INSULATION));
    }

    public static boolean isInsulatable(ItemStack stack)
    {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null
                && equippable.slot().isArmor()
                && !InsulationRegistry.hasArmorInsulation(stack);
    }

    public static int getInsulationSlots(ItemStack stack)
    {
        if (!isInsulatable(stack))
        {
            return 0;
        }

        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null)
        {
            return 0;
        }

        return switch (equippable.slot())
        {
            case HEAD -> 4;
            case CHEST -> 6;
            case LEGS -> 5;
            case FEET -> 4;
            default -> 0;
        };
    }

    public static boolean canAddInsulationItem(ItemStack armor, ItemStack insulator)
    {
        if (!isInsulatable(armor) || !InsulationRegistry.hasItemInsulation(insulator))
        {
            return false;
        }

        int filled = getExistingInsulationCap(armor)
                .map(cap -> cap.getInsulation().size())
                .orElse(0);

        return filled < getInsulationSlots(armor);
    }

    public static boolean addInsulationItem(ItemStack armor, ItemStack insulator)
    {
        if (!canAddInsulationItem(armor, insulator))
        {
            return false;
        }

        ItemInsulationCap cap = getInsulationCap(armor).orElseThrow();
        ItemInsulationCap updated = cap.addInsulationItem(insulator);
        if (updated == cap)
        {
            return false;
        }

        armor.set(ModItemComponents.ARMOR_INSULATION, updated);
        return true;
    }

    public static Optional<ItemStack> removeLastInsulationItem(ItemStack armor)
    {
        Optional<ItemInsulationCap> existing = getExistingInsulationCap(armor);
        if (existing.isEmpty())
        {
            return Optional.empty();
        }

        ItemInsulationCap cap = existing.get();
        Optional<ItemStack> removed = cap.getLastInsulationItem();
        if (removed.isEmpty())
        {
            return Optional.empty();
        }

        armor.set(ModItemComponents.ARMOR_INSULATION, cap.removeLast());
        return removed;
    }

    private ItemInsulationManager()
    {
    }
}
