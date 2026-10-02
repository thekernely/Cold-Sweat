package com.momosoftworks.coldsweat.api.registry;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Fabric-side boundary for Cold Sweat's built-in thermal-machine fuel defaults.
 *
 * Upstream sources these values from ItemSettingsConfig/FuelData. Keeping the
 * defaults in one registry preserves those semantics now and leaves a clean
 * replacement point for the full configurable data layer later.
 */
public final class ThermalFuelRegistry
{
    public static int getBoilerFuel(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return 0;
        }

        if (stack.is(ItemTags.PLANKS))
        {
            return 10;
        }
        if (stack.is(ItemTags.COALS))
        {
            return 55;
        }
        if (stack.is(ItemTags.LOGS_THAT_BURN))
        {
            return 40;
        }
        if (stack.is(Items.DRIED_KELP_BLOCK))
        {
            return 40;
        }
        if (stack.is(Items.COAL_BLOCK))
        {
            return 500;
        }
        if (stack.is(Items.MAGMA_BLOCK))
        {
            return 333;
        }
        if (stack.is(Items.LAVA_BUCKET))
        {
            return 1000;
        }
        return 0;
    }

    public static int getIceboxFuel(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return 0;
        }

        if (stack.is(Items.SNOWBALL))
        {
            return 10;
        }
        if (stack.is(Items.CLAY_BALL))
        {
            return 37;
        }
        if (stack.is(Items.SNOW_BLOCK))
        {
            return 40;
        }
        if (stack.is(Items.ICE))
        {
            return 250;
        }
        if (stack.is(Items.CLAY))
        {
            return 333;
        }
        if (stack.is(Items.POWDER_SNOW_BUCKET))
        {
            return 100;
        }
        if (stack.is(Items.PACKED_ICE))
        {
            return 1000;
        }
        return 0;
    }

    public static int getHearthFuel(ItemStack stack)
    {
        int hot = getBoilerFuel(stack);
        return hot != 0 ? hot : -getIceboxFuel(stack);
    }

    private ThermalFuelRegistry()
    {
    }
}
