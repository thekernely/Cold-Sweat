package com.momosoftworks.coldsweat.common.item;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Displays the player's current ambient Cold Sweat temperature.
 *
 * The preference/config bridge is not ported yet, so the 26.2 Fabric runtime
 * uses Celsius as the temporary display default.
 */
public final class ThermometerItem extends Item
{
    public ThermometerItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        if (!level.isClientSide())
        {
            double worldTemperature = Temperature.get(player, Temperature.Trait.WORLD);
            int celsius = (int) Math.round(
                    Temperature.convert(
                            worldTemperature,
                            Temperature.Units.MC,
                            Temperature.Units.C,
                            true
                    )
            );

            if (player instanceof ServerPlayer serverPlayer)
            {
                serverPlayer.sendOverlayMessage(
                        Component.literal(
                                celsius + " "
                                        + Temperature.Units.C.getFormattedName().getString()
                        )
                );
            }
        }

        return InteractionResult.SUCCESS;
    }
}
