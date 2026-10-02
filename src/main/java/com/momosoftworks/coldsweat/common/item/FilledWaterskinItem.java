package com.momosoftworks.coldsweat.common.item;

import com.momosoftworks.coldsweat.api.registry.ItemTemperatureRegistry;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.core.init.ModSounds;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Filled waterskin gameplay boundary for Fabric 26.2.
 *
 * Normal use drinks the water. Crouch-use pours it over the player, restoring
 * the upstream hot/cold wetness interaction without client-only particle work.
 */
public final class FilledWaterskinItem extends Item
{
    public FilledWaterskinItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if (!player.isCrouching())
        {
            return super.use(level, player, hand);
        }

        ItemStack stack = player.getItemInHand(hand);
        double waterTemperature = stack.getOrDefault(
                ModItemComponents.WATER_TEMPERATURE,
                0.0
        );

        if (!level.isClientSide())
        {
            ItemTemperatureRegistry.applyWaterskinPour(
                    player,
                    waterTemperature
            );

            player.playSound(
                    ModSounds.WATERSKIN_POUR.value(),
                    2.0F,
                    0.9F + player.getRandom().nextFloat() * 0.2F
            );

            if (!player.isCreative())
            {
                player.setItemInHand(
                        hand,
                        new ItemStack(ModItems.WATERSKIN)
                );
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public ItemStack finishUsingItem(
            ItemStack stack,
            Level level,
            LivingEntity entity
    )
    {
        double waterTemperature = stack.getOrDefault(
                ModItemComponents.WATER_TEMPERATURE,
                0.0
        );

        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (!level.isClientSide())
        {
            ItemTemperatureRegistry.applyWaterskinDrink(
                    entity,
                    waterTemperature
            );
        }

        if (entity instanceof Player player && player.isCreative())
        {
            return stack;
        }

        return result.isEmpty()
                ? new ItemStack(ModItems.WATERSKIN)
                : result;
    }
}
