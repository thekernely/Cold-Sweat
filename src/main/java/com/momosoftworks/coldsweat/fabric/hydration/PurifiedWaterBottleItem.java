package com.momosoftworks.coldsweat.fabric.hydration;

import com.momosoftworks.coldsweat.api.util.Hydration;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Safe, heat-purified bottled water.
 *
 * Raw bottled water deliberately remains Minecraft's normal water potion item;
 * this class only exists for the new purified state.
 */
public final class PurifiedWaterBottleItem extends Item
{
    public PurifiedWaterBottleItem(
            Properties properties
    )
    {
        super(properties);
    }

    @Override
    public ItemUseAnimation getUseAnimation(
            ItemStack stack
    )
    {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public int getUseDuration(
            ItemStack stack,
            LivingEntity entity
    )
    {
        return 32;
    }

    @Override
    public InteractionResult use(
            Level level,
            Player player,
            InteractionHand hand
    )
    {
        if (Hydration.get(player)
                >= Hydration.MAX_HYDRATION - 1.0e-6)
        {
            return InteractionResult.FAIL;
        }

        return ItemUtils.startUsingInstantly(
                level,
                player,
                hand
        );
    }

    @Override
    public ItemStack finishUsingItem(
            ItemStack stack,
            Level level,
            LivingEntity entity
    )
    {
        if (entity instanceof ServerPlayer player)
        {
            CriteriaTriggers.CONSUME_ITEM.trigger(
                    player,
                    stack
            );

            HydrationGameplayRuntime.consumePurifiedWater(
                    player
            );
        }

        stack.setCount(
                stack.getCount() - 1
        );

        return stack;
    }
}
