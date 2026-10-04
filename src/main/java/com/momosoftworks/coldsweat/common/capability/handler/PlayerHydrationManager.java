package com.momosoftworks.coldsweat.common.capability.handler;

import com.momosoftworks.coldsweat.common.capability.hydration.HydrationData;
import com.momosoftworks.coldsweat.core.init.ModDataAttachments;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * Fabric-side ownership boundary for player hydration state.
 *
 * M8.1 mirrors the temperature attachment lifecycle:
 * - persistent across normal saves/logouts
 * - synchronized to clients
 * - copied for non-death player replacement
 * - reset to defaults after death
 */
public final class PlayerHydrationManager
{
    public static Optional<HydrationData> getHydrationData(Entity entity)
    {
        if (!(entity instanceof Player player))
        {
            return Optional.empty();
        }

        return Optional.of(
                player.getAttachedOrCreate(
                        ModDataAttachments.PLAYER_HYDRATION
                )
        );
    }

    public static boolean setHydrationData(
            Player player,
            HydrationData data
    )
    {
        player.setAttached(
                ModDataAttachments.PLAYER_HYDRATION,
                data
        );
        return true;
    }

    private static void registerPlayerLoadLifecycle()
    {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) ->
        {
            if (entity instanceof Player player)
            {
                player.getAttachedOrCreate(
                        ModDataAttachments.PLAYER_HYDRATION
                );
            }
        });
    }

    private static void registerPlayerCopyLifecycle()
    {
        ServerPlayerEvents.COPY_FROM.register(
                (oldPlayer, newPlayer, alive) ->
                {
                    /*
                     * Death deliberately resets hydration, matching vanilla
                     * hunger-style survival state. Non-death replacement
                     * (for example dimension/clone lifecycle) preserves it.
                     */
                    if (!alive)
                    {
                        return;
                    }

                    getHydrationData(oldPlayer).ifPresent(data ->
                            setHydrationData(
                                    newPlayer,
                                    data
                            )
                    );
                }
        );
    }

    public static void initialize()
    {
        registerPlayerLoadLifecycle();
        registerPlayerCopyLifecycle();

        ColdSweatFabric.LOGGER.info(
                "Initializing Cold Sweat player hydration state and lifecycle hooks."
        );
    }

    private PlayerHydrationManager()
    {
    }
}
