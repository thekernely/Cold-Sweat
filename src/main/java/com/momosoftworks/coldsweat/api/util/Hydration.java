package com.momosoftworks.coldsweat.api.util;

import com.momosoftworks.coldsweat.common.capability.handler.PlayerHydrationManager;
import com.momosoftworks.coldsweat.common.capability.hydration.HydrationData;
import net.minecraft.world.entity.player.Player;

/**
 * Minimal public read/write surface for Cold Sweat hydration state.
 *
 * Server-side gameplay systems should own mutations. Client callers may use
 * the getters to read the synchronized attachment for HUD/presentation.
 */
public final class Hydration
{
    public static final double MAX_HYDRATION =
            HydrationData.MAX_HYDRATION;

    private Hydration()
    {
    }

    public static double get(Player player)
    {
        return PlayerHydrationManager.getHydrationData(player)
                .map(HydrationData::hydration)
                .orElse(HydrationData.DEFAULT_HYDRATION);
    }

    public static double getSaturation(Player player)
    {
        return PlayerHydrationManager.getHydrationData(player)
                .map(HydrationData::saturation)
                .orElse(HydrationData.DEFAULT_SATURATION);
    }

    public static double getExhaustion(Player player)
    {
        return PlayerHydrationManager.getHydrationData(player)
                .map(HydrationData::exhaustion)
                .orElse(HydrationData.DEFAULT_EXHAUSTION);
    }

    public static void set(
            Player player,
            double value
    )
    {
        mutate(
                player,
                data -> data.withHydration(value)
        );
    }

    public static void add(
            Player player,
            double amount
    )
    {
        mutate(
                player,
                data -> data.withHydration(
                        data.hydration() + amount
                )
        );
    }

    public static void setSaturation(
            Player player,
            double value
    )
    {
        mutate(
                player,
                data -> data.withSaturation(value)
        );
    }

    public static void addSaturation(
            Player player,
            double amount
    )
    {
        mutate(
                player,
                data -> data.withSaturation(
                        data.saturation() + amount
                )
        );
    }

    public static void setExhaustion(
            Player player,
            double value
    )
    {
        mutate(
                player,
                data -> data.withExhaustion(value)
        );
    }

    public static void addExhaustion(
            Player player,
            double amount
    )
    {
        mutate(
                player,
                data -> data.withExhaustion(
                        data.exhaustion() + amount
                )
        );
    }

    private static void mutate(
            Player player,
            java.util.function.UnaryOperator<HydrationData> mutation
    )
    {
        PlayerHydrationManager.getHydrationData(player)
                .ifPresent(data ->
                {
                    HydrationData updated =
                            mutation.apply(data);

                    if (updated != data)
                    {
                        PlayerHydrationManager.setHydrationData(
                                player,
                                updated
                        );
                    }
                });
    }
}
