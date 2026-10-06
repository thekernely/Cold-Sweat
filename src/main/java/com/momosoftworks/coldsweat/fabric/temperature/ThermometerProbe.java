package com.momosoftworks.coldsweat.fabric.temperature;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.capability.temperature.TemperatureModifierRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * On-demand nearby environmental probe used by the thermometer.
 *
 * The target is always within ordinary interaction reach, so the player's
 * cached ambient-climate baseline is reused while the expensive local spatial
 * state is re-sampled at the target. This gives real room-air and radiant heat
 * differences without introducing a second continuously-running climate model.
 * M9 can replace the ambient baseline with the generalized arbitrary-position
 * climate service once seasonal/shelter ownership is finalized.
 */
public final class ThermometerProbe
{
    /*
     * The thermometer has no gameplay cooldown. This tiny same-position cache
     * only prevents rapid right-click spam from rerunning the ~10k-block spatial
     * scan multiple times during the same half-second.
     */
    private static final long CACHE_TICKS = 10L;

    private static final Map<ServerPlayer, CachedProbe> LAST_PROBE =
            new WeakHashMap<>();

    private ThermometerProbe()
    {
    }

    public static double sample(
            ServerPlayer player,
            BlockPos targetPos
    )
    {
        if (!(player.level() instanceof ServerLevel level))
        {
            return Temperature.get(
                    player,
                    Temperature.Trait.WORLD
            );
        }

        BlockPos immutableTarget =
                targetPos.immutable();

        long now = level.getGameTime();
        CachedProbe cached = LAST_PROBE.get(player);

        if (cached != null
                && cached.position().equals(immutableTarget)
                && now >= cached.gameTime()
                && now - cached.gameTime() <= CACHE_TICKS)
        {
            return cached.temperatureMc();
        }

        double ambientClimate =
                TemperatureModifierRuntime
                        .getEnvironmentSnapshot(player)
                        .map(EnvironmentSnapshot::ambientClimate)
                        .orElseGet(() ->
                                Temperature.get(
                                        player,
                                        Temperature.Trait.WORLD
                                )
                        );

        EnvironmentSnapshotScanner.ScanResult scan =
                EnvironmentSnapshotScanner.scanAt(
                        level,
                        immutableTarget
                );

        RoomThermalState room =
                RoomThermalManager.update(
                        level,
                        scan.room(),
                        ambientClimate
                );

        double localAir =
                room.available()
                        ? room.airTemperatureMc()
                        : ambientClimate;

        double radiantDelta =
                ApparentTemperatureModel
                        .radiantTemperatureDelta(
                                scan.spatial()
                        );

        double measured =
                localAir + radiantDelta;

        LAST_PROBE.put(
                player,
                new CachedProbe(
                        immutableTarget,
                        now,
                        measured
                )
        );

        return measured;
    }

    private record CachedProbe(
            BlockPos position,
            long gameTime,
            double temperatureMc
    )
    {
    }
}
