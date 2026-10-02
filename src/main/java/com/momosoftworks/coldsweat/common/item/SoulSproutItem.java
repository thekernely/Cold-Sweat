package com.momosoftworks.coldsweat.common.item;

import com.momosoftworks.coldsweat.api.registry.ItemTemperatureRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * M5 gameplay half of the Soul Sprout item.
 *
 * The upstream item is also the placeable item for Soul Stalk and has dispenser
 * planting behavior. Soul Stalk is an M6 block, so those block-facing hooks are
 * deliberately deferred rather than pulling M6 into the item milestone.
 */
public final class SoulSproutItem extends Item
{
    public SoulSproutItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(
            ItemStack stack,
            Level level,
            LivingEntity entity
    )
    {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        entity.clearFire();

        if (entity instanceof ServerPlayer player)
        {
            ItemTemperatureRegistry.applyConsumed(player, this);
        }

        return result;
    }
}
