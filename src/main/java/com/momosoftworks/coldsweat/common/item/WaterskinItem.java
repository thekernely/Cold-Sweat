package com.momosoftworks.coldsweat.common.item;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.core.init.ModItemComponents;
import com.momosoftworks.coldsweat.core.init.ModItems;
import com.momosoftworks.coldsweat.core.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.sounds.SoundSource;
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
 * Empty Waterskin source-water and cauldron fill paths.
 *
 * M8.10b stores the fill-time effective environment as Celsius. Natural water
 * is always untreated; the cooking path may later make it Very Warm + Purified.
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

        playFillFeedback(
                level,
                player,
                hand,
                hit.getBlockPos()
        );

        if (!level.isClientSide())
        {
            fill(
                    player,
                    player.getItemInHand(hand),
                    hand
            );
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

        playFillFeedback(
                level,
                player,
                context.getHand(),
                pos
        );

        if (!level.isClientSide())
        {
            if (!player.isCreative())
            {
                LayeredCauldronBlock.lowerFillLevel(
                        state,
                        level,
                        pos
                );
            }

            fill(
                    player,
                    context.getItemInHand(),
                    context.getHand()
            );
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
                getFillTemperatureCelsius(player)
        );
        filled.set(
                ModItemComponents.WATERSKIN_PURIFIED,
                false
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

    }

    /**
     * Successful fills should feel like a physical container interaction, even
     * though empty and filled Waterskins are separate registered items.
     *
     * On the client this gives the initiating player zero-latency feedback. On
     * the server the same call broadcasts the sound/swing to nearby players;
     * passing the initiating player to playSound prevents a duplicate sound
     * from being sent back to that client.
     */
    private static void playFillFeedback(
            Level level,
            Player player,
            InteractionHand hand,
            BlockPos sourcePos
    )
    {
        float pitch =
                0.9F
                        + player.getRandom().nextFloat()
                        * 0.2F;

        level.playSound(
                player,
                sourcePos,
                ModSounds.WATERSKIN_FILL.value(),
                SoundSource.PLAYERS,
                1.0F,
                pitch
        );

        player.swing(hand);
    }

    private static double getFillTemperatureCelsius(Player player)
    {
        /*
         * Fabric currently has no arbitrary-position equivalent of upstream's
         * WorldHelper.getTemperatureAt without pulling its NeoForge-only graph
         * back into the source set. A Waterskin can only fill within ordinary
         * interaction reach, so the already-synchronized effective WORLD value
         * is the authoritative local proxy until M9 introduces the dedicated
         * position climate service.
         */
        return Temperature.convert(
                Temperature.get(
                        player,
                        Temperature.Trait.WORLD
                ),
                Temperature.Units.MC,
                Temperature.Units.C,
                true
        );
    }
}
