package com.momosoftworks.coldsweat.common.item;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.fabric.temperature.ThermometerProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * Reusable precision probe for local environmental temperature.
 *
 * The normal HUD deliberately keeps the environment readout coarse and
 * continuous. The thermometer instead provides a precise one-decimal reading
 * on demand, and can inspect the nearby air cell on the face of a clicked
 * block so rooms, shelters and local radiant sources can be compared directly.
 */
public final class ThermometerItem extends Item
{
    public ThermometerItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult use(
            Level level,
            Player player,
            InteractionHand hand
    )
    {
        if (!level.isClientSide()
                && player instanceof ServerPlayer serverPlayer)
        {
            displayReading(
                    serverPlayer,
                    Temperature.get(
                            serverPlayer,
                            Temperature.Trait.WORLD
                    ),
                    false
            );

            serverPlayer.swing(hand, true);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(
            UseOnContext context
    )
    {
        Player player = context.getPlayer();
        if (player == null)
        {
            return InteractionResult.PASS;
        }

        if (!context.getLevel().isClientSide()
                && player instanceof ServerPlayer serverPlayer)
        {
            /*
             * Probe the air immediately outside the clicked face. This makes
             * the result correspond to the place the thermometer tip reaches
             * instead of to the solid block's own cell.
             */
            BlockPos probePos =
                    context.getClickedPos()
                            .relative(context.getClickedFace());

            displayReading(
                    serverPlayer,
                    ThermometerProbe.sample(
                            serverPlayer,
                            probePos
                    ),
                    true
            );

            serverPlayer.swing(
                    context.getHand(),
                    true
            );
        }

        return InteractionResult.SUCCESS;
    }

    private static void displayReading(
            ServerPlayer player,
            double worldTemperature,
            boolean targeted
    )
    {
        double celsius =
                Temperature.convert(
                        worldTemperature,
                        Temperature.Units.MC,
                        Temperature.Units.C,
                        true
                );

        String value =
                String.format(
                        Locale.ROOT,
                        "%.1f",
                        celsius
                );

        player.sendOverlayMessage(
                Component.translatable(
                        targeted
                                ? "item.cold_sweat.thermometer.target_reading"
                                : "item.cold_sweat.thermometer.local_reading",
                        value,
                        Temperature.Units.C.getFormattedName()
                )
        );
    }
}
