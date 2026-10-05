package com.momosoftworks.coldsweat.common.item;

import com.momosoftworks.coldsweat.api.registry.ItemTemperatureRegistry;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.core.init.ModSounds;
import com.momosoftworks.coldsweat.fabric.hydration.HydrationGameplayRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * M8.10b one-use thermal drink.
 *
 * Filled Waterskins are intentionally not passive inventory heaters and no
 * longer have a player-pour action. Drinking is the only player-use path:
 * hydration/contamination behave like ordinary water while Warm/Very Warm
 * water may apply a bounded Warming state to CORE.
 */
public final class FilledWaterskinItem extends Item
{
    public FilledWaterskinItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (!state.is(Blocks.CAULDRON)
                && !state.is(Blocks.WATER_CAULDRON))
        {
            return InteractionResult.PASS;
        }

        int waterLevel = state.is(Blocks.WATER_CAULDRON)
                ? state.getValue(LayeredCauldronBlock.LEVEL)
                : 0;

        if (waterLevel >= 3)
        {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide())
        {
            BlockState filledState = waterLevel == 0
                    ? Blocks.WATER_CAULDRON
                            .defaultBlockState()
                            .setValue(LayeredCauldronBlock.LEVEL, 1)
                    : state.setValue(
                            LayeredCauldronBlock.LEVEL,
                            waterLevel + 1
                    );

            level.setBlock(pos, filledState, 3);
            level.playSound(
                    null,
                    pos,
                    ModSounds.WATERSKIN_FILL.value(),
                    SoundSource.BLOCKS,
                    2.0F,
                    0.9F + (float) Math.random() * 0.2F
            );

            Player player = context.getPlayer();
            if (player != null && !player.isCreative())
            {
                player.setItemInHand(
                        context.getHand(),
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
        double waterCelsius = storedWaterCelsius(stack);
        boolean purified = isPurified(stack);

        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (!level.isClientSide()
                && entity instanceof ServerPlayer serverPlayer)
        {
            if (purified)
            {
                HydrationGameplayRuntime.consumePurifiedWater(serverPlayer);
            }
            else
            {
                HydrationGameplayRuntime.consumeRawWater(serverPlayer);
            }

            ItemTemperatureRegistry.applyWaterskinDrink(
                    serverPlayer,
                    waterCelsius
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

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag tooltipFlag
    )
    {
        ThermalState state = thermalState(stack);

        String temperatureKey = switch (state)
        {
            case COLD -> "tooltip.cold_sweat.waterskin.cold";
            case WARM -> "tooltip.cold_sweat.waterskin.warm";
            case VERY_WARM -> "tooltip.cold_sweat.waterskin.very_warm";
        };

        ChatFormatting temperatureColor = switch (state)
        {
            case COLD -> ChatFormatting.BLUE;
            case WARM -> ChatFormatting.GOLD;
            case VERY_WARM -> ChatFormatting.RED;
        };

        builder.accept(
                Component.translatable(temperatureKey)
                        .withStyle(temperatureColor)
        );

        builder.accept(
                Component.translatable(
                        isPurified(stack)
                                ? "tooltip.cold_sweat.waterskin.purified"
                                : "tooltip.cold_sweat.waterskin.untreated"
                ).withStyle(
                        isPurified(stack)
                                ? ChatFormatting.AQUA
                                : ChatFormatting.GRAY
                )
        );
    }

    public static double storedWaterCelsius(ItemStack stack)
    {
        return stack.getOrDefault(
                ModItemComponents.WATER_TEMPERATURE,
                0.0
        );
    }

    public static ThermalState thermalState(ItemStack stack)
    {
        double celsius = storedWaterCelsius(stack);

        if (celsius >= ItemTemperatureRegistry.VERY_WARM_WATER_THRESHOLD_C)
        {
            return ThermalState.VERY_WARM;
        }

        if (celsius >= ItemTemperatureRegistry.WARM_WATER_THRESHOLD_C)
        {
            return ThermalState.WARM;
        }

        return ThermalState.COLD;
    }

    public static boolean isPurified(ItemStack stack)
    {
        return stack.getOrDefault(
                ModItemComponents.WATERSKIN_PURIFIED,
                false
        );
    }

    public static boolean isVeryWarm(ItemStack stack)
    {
        return thermalState(stack) == ThermalState.VERY_WARM;
    }

    public static void heatAndPurify(ItemStack stack)
    {
        stack.set(
                ModItemComponents.WATER_TEMPERATURE,
                ItemTemperatureRegistry.HEATED_WATERSKIN_C
        );
        stack.set(
                ModItemComponents.WATERSKIN_PURIFIED,
                true
        );
    }

    public enum ThermalState
    {
        COLD,
        WARM,
        VERY_WARM
    }
}
