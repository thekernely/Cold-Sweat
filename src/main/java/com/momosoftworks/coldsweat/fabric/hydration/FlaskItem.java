package com.momosoftworks.coldsweat.fabric.hydration;

import com.momosoftworks.coldsweat.api.util.Hydration;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * M8.5/M8.6 reusable hydration container.
 *
 * Capacity belongs to the item tier. Mutable water/filter state lives on the
 * ItemStack through synchronized persistent data components so vanilla
 * smithing transformations carry it forward during upgrades.
 *
 * One stored unit is one drink/use. Water quality controls hydration gain:
 * raw = +2 with contamination risk; purified = +3 and safe.
 */
public final class FlaskItem extends Item
{
    public static final int MAX_FILTER_CHARGES = 5;

    private static final int RAW_BAR_COLOR = 0x3C8FD6;
    private static final int PURIFIED_BAR_COLOR = 0x71DCEB;

    private final int capacity;

    public FlaskItem(
            Properties properties,
            int capacity
    )
    {
        super(properties);

        if (capacity <= 0)
        {
            throw new IllegalArgumentException(
                    "Flask capacity must be positive"
            );
        }

        this.capacity = capacity;
    }

    public int capacity()
    {
        return capacity;
    }

    @Override
    public InteractionResult use(
            Level level,
            Player player,
            InteractionHand hand
    )
    {
        /*
         * Looking at source water with a non-full flask means "refill".
         * Otherwise normal use means "drink".
         */
        InteractionResult refill =
                HydrationGameplayRuntime.tryFillFlask(
                        player,
                        level,
                        hand
                );

        if (refill != InteractionResult.PASS)
        {
            return refill;
        }

        ItemStack stack =
                player.getItemInHand(hand);

        if (waterAmount(stack) <= 0
                || Hydration.get(player)
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
    public ItemStack finishUsingItem(
            ItemStack stack,
            Level level,
            LivingEntity entity
    )
    {
        if (!level.isClientSide()
                && entity instanceof ServerPlayer player
                && waterAmount(stack) > 0)
        {
            if (isPurified(stack))
            {
                HydrationGameplayRuntime.consumePurifiedWater(
                        player
                );
            }
            else
            {
                HydrationGameplayRuntime.consumeRawWater(
                        player
                );
            }

            setWaterAmount(
                    stack,
                    waterAmount(stack) - 1
            );
        }

        return stack;
    }

    @Override
    public boolean isBarVisible(
            ItemStack stack
    )
    {
        /*
         * Always show the flask gauge, including on empty/full flasks. The bar
         * is container state, not damage, so hiding it at either endpoint
         * would remove useful at-a-glance information.
         */
        return true;
    }

    @Override
    public int getBarWidth(
            ItemStack stack
    )
    {
        int max =
                capacity(stack);

        if (max <= 0)
        {
            return 0;
        }

        int amount =
                waterAmount(stack);

        if (amount <= 0)
        {
            return 0;
        }

        return Math.max(
                1,
                Math.min(
                        13,
                        Math.round(
                                13.0F
                                        * amount
                                        / max
                        )
                )
        );
    }

    @Override
    public int getBarColor(
            ItemStack stack
    )
    {
        return isPurified(stack)
                ? PURIFIED_BAR_COLOR
                : RAW_BAR_COLOR;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag tooltipFlag
    )
    {
        int amount =
                waterAmount(stack);

        int max =
                capacity(stack);

        if (amount <= 0)
        {
            builder.accept(
                    Component.translatable(
                            "tooltip.cold_sweat.flask.empty"
                    ).withStyle(ChatFormatting.GRAY)
            );
        }
        else if (isPurified(stack))
        {
            builder.accept(
                    Component.translatable(
                            "tooltip.cold_sweat.flask.purified"
                    ).withStyle(ChatFormatting.AQUA)
            );
        }
        else
        {
            builder.accept(
                    Component.translatable(
                            "tooltip.cold_sweat.flask.raw"
                    ).withStyle(ChatFormatting.BLUE)
            );
        }

        builder.accept(
                Component.translatable(
                        "tooltip.cold_sweat.flask.uses",
                        amount,
                        max
                ).withStyle(ChatFormatting.GRAY)
        );

        int filterCharges =
                filterCharges(stack);

        if (filterCharges > 0)
        {
            builder.accept(
                    Component.translatable(
                            "tooltip.cold_sweat.flask.filter",
                            filterCharges,
                            MAX_FILTER_CHARGES
                    ).withStyle(ChatFormatting.DARK_GRAY)
            );
        }
    }

    public static boolean isFlask(
            ItemStack stack
    )
    {
        return stack.getItem() instanceof FlaskItem;
    }

    public static int capacity(
            ItemStack stack
    )
    {
        return stack.getItem() instanceof FlaskItem flask
                ? flask.capacity()
                : 0;
    }

    public static int waterAmount(
            ItemStack stack
    )
    {
        return stack.getOrDefault(
                ModItemComponents.FLASK_WATER_AMOUNT,
                0
        );
    }

    public static boolean isPurified(
            ItemStack stack
    )
    {
        return stack.getOrDefault(
                ModItemComponents.FLASK_PURIFIED,
                false
        );
    }

    public static int filterCharges(
            ItemStack stack
    )
    {
        return stack.getOrDefault(
                ModItemComponents.FLASK_FILTER_CHARGES,
                0
        );
    }

    public static void setWaterAmount(
            ItemStack stack,
            int amount
    )
    {
        int clamped =
                Math.max(
                        0,
                        Math.min(
                                capacity(stack),
                                amount
                        )
                );

        stack.set(
                ModItemComponents.FLASK_WATER_AMOUNT,
                clamped
        );

        if (clamped == 0)
        {
            stack.set(
                    ModItemComponents.FLASK_PURIFIED,
                    false
            );
        }
    }

    public static void setPurified(
            ItemStack stack,
            boolean purified
    )
    {
        stack.set(
                ModItemComponents.FLASK_PURIFIED,
                waterAmount(stack) > 0
                        && purified
        );
    }

    public static void setFilterCharges(
            ItemStack stack,
            int charges
    )
    {
        stack.set(
                ModItemComponents.FLASK_FILTER_CHARGES,
                Math.max(
                        0,
                        Math.min(
                                MAX_FILTER_CHARGES,
                                charges
                        )
                )
        );
    }

    public static void setState(
            ItemStack stack,
            int amount,
            boolean purified,
            int filterCharges
    )
    {
        setWaterAmount(
                stack,
                amount
        );

        setPurified(
                stack,
                purified
        );

        setFilterCharges(
                stack,
                filterCharges
        );
    }

    public static void copyState(
            ItemStack source,
            ItemStack target
    )
    {
        setState(
                target,
                waterAmount(source),
                isPurified(source),
                filterCharges(source)
        );
    }
}
