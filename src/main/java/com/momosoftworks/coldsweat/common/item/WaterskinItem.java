package com.momosoftworks.coldsweat.common.item;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureRuntime;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.core.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Empty waterskin. Restores the upstream source-water and water-cauldron fill
 * paths without pulling NeoForge fluid capabilities into the Fabric port.
 */
public final class WaterskinItem extends Item
{
    public WaterskinItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        BlockHitResult hit = getPlayerPOVHitResult(
                level,
                player,
                ClipContext.Fluid.SOURCE_ONLY
        );

        if (hit.getType() != HitResult.Type.BLOCK)
        {
            return InteractionResult.PASS;
        }

        BlockState state = level.getBlockState(hit.getBlockPos());
        if (!state.getFluidState().isSource()
                || !state.getFluidState().is(FluidTags.WATER))
        {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide())
        {
            fill(player, player.getItemInHand(hand), hand);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        if (player == null)
        {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (!state.is(Blocks.WATER_CAULDRON))
        {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide())
        {
            if (!player.isCreative())
            {
                LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            }
            fill(player, context.getItemInHand(), context.getHand());
        }

        return InteractionResult.SUCCESS;
    }

    private static void fill(
            Player player,
            ItemStack emptyStack,
            InteractionHand hand
    )
    {
        ItemStack filled = new ItemStack(ModItems.FILLED_WATERSKIN);
        filled.set(
                ModItemComponents.WATER_TEMPERATURE,
                getFillTemperature(player)
        );

        if (player.isCreative())
        {
            if (!player.getInventory().add(filled))
            {
                player.drop(filled, false);
            }
        }
        else if (emptyStack.getCount() > 1)
        {
            emptyStack.shrink(1);
            if (!player.getInventory().add(filled))
            {
                player.drop(filled, false);
            }
        }
        else
        {
            player.setItemInHand(hand, filled);
        }

        player.playSound(
                ModSounds.WATERSKIN_FILL.value(),
                2.0F,
                0.9F + player.getRandom().nextFloat() * 0.2F
        );
    }

    private static double getFillTemperature(Player player)
    {
        double world = Temperature.get(player, Temperature.Trait.WORLD);
        double freezing = Temperature.get(player, Temperature.Trait.FREEZING_POINT);
        double burning = Temperature.get(player, Temperature.Trait.BURNING_POINT);

        if (Double.compare(freezing, 0.0) == 0
                && Double.compare(burning, 0.0) == 0)
        {
            freezing = TemperatureRuntime.DEFAULT_FREEZING_POINT;
            burning = TemperatureRuntime.DEFAULT_BURNING_POINT;
        }

        double neutral = (freezing + burning) / 2.0;
        return Math.max(
                -50.0,
                Math.min(50.0, (world - neutral) * 15.0)
        );
    }
}
