package com.momosoftworks.coldsweat.fabric.temperature;

import com.momosoftworks.coldsweat.api.registry.BlockTempRegistry;
import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Low-frequency spatial environment pass inspired by Homeostatic's unified
 * Environment scan.
 *
 * M7.12f-b is measurement-only. None of these values alter gameplay yet.
 *
 * One 25 x 25 x 15 pass currently measures:
 * - local and wide sky exposure for shelter/underground classification
 * - nearby water volume
 * - positive thermal-source geometry / provisional radiant load
 *
 * Chunk lookups are cached for the duration of the pass and unloaded chunks
 * are never forced to load. The inner loop reuses one MutableBlockPos.
 */
public final class EnvironmentSnapshotScanner
{
    public static final int HORIZONTAL_RADIUS = 12;
    public static final int VERTICAL_BELOW = 3;
    public static final int VERTICAL_ABOVE = 11;

    private static final int SHELTER_RADIUS = 2;
    private static final int WATER_RADIUS = 5;
    private static final int WATER_MAX_Y_OFFSET = 5;
    private static final int RADIANT_MAX_Y_OFFSET = 3;

    private EnvironmentSnapshotScanner()
    {
    }

    public static EnvironmentSnapshot.SpatialState scan(
            LivingEntity entity
    )
    {
        if (!(entity.level() instanceof ServerLevel level))
        {
            return EnvironmentSnapshot.SpatialState.unavailable();
        }

        BlockPos origin = entity.blockPosition();
        BlockPos eyePos = BlockPos.containing(entity.getEyePosition());
        Vec3 entityCenter = entity.getBoundingBox().getCenter();

        Map<Long, LevelChunk> chunkCache = new HashMap<>();
        Map<BlockTemp, Double> strongestRadiantSources =
                new IdentityHashMap<>();

        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos skyCursor =
                new BlockPos.MutableBlockPos();

        int scannedBlocks = 0;

        int localSkySamples = 0;
        int localSkyVisible = 0;
        int wideSkySamples = 0;
        int wideSkyVisible = 0;

        int waterSamples = 0;
        int waterBlocks = 0;

        int radiantSourceBlocks = 0;
        double additiveRadiantLoad = 0.0;

        boolean meaningfulSky =
                level.dimensionType().hasSkyLight()
                        && !level.dimensionType().hasCeiling();

        for (int x = -HORIZONTAL_RADIUS;
             x <= HORIZONTAL_RADIUS;
             x++)
        {
            for (int z = -HORIZONTAL_RADIUS;
                 z <= HORIZONTAL_RADIUS;
                 z++)
            {
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;

                LevelChunk chunk = getLoadedChunk(
                        level,
                        worldX >> 4,
                        worldZ >> 4,
                        chunkCache
                );

                if (chunk == null)
                {
                    continue;
                }

                /*
                 * One sky query per horizontal column. A small 5x5 kernel is
                 * used for immediate shelter while the complete 25x25 field
                 * distinguishes a roof/house from genuinely underground space.
                 */
                if (meaningfulSky)
                {
                    skyCursor.set(
                            worldX,
                            eyePos.getY() + 1,
                            worldZ
                    );

                    if (level.isInWorldBounds(skyCursor))
                    {
                        boolean visible =
                                level.canSeeSky(skyCursor);

                        wideSkySamples++;
                        if (visible)
                        {
                            wideSkyVisible++;
                        }

                        if (Math.abs(x) <= SHELTER_RADIUS
                                && Math.abs(z) <= SHELTER_RADIUS)
                        {
                            localSkySamples++;
                            if (visible)
                            {
                                localSkyVisible++;
                            }
                        }
                    }
                }

                for (int y = -VERTICAL_BELOW;
                     y <= VERTICAL_ABOVE;
                     y++)
                {
                    cursor.set(
                            worldX,
                            origin.getY() + y,
                            worldZ
                    );

                    if (!level.isInWorldBounds(cursor))
                    {
                        continue;
                    }

                    BlockState state =
                            chunk.getBlockState(cursor);
                    scannedBlocks++;

                    if (Math.abs(x) <= WATER_RADIUS
                            && Math.abs(z) <= WATER_RADIUS
                            && y <= WATER_MAX_Y_OFFSET)
                    {
                        waterSamples++;

                        if (state.getFluidState()
                                .is(FluidTags.WATER))
                        {
                            waterBlocks++;
                        }
                    }

                    if (y > RADIANT_MAX_Y_OFFSET
                            || state.isAir())
                    {
                        continue;
                    }

                    Collection<BlockTemp> blockTemps =
                            BlockTempRegistry.getBlockTempsFor(
                                    state
                            );

                    if (blockTemps.size() == 1
                            && blockTemps.contains(
                                    BlockTempRegistry.DEFAULT_BLOCK_TEMP
                            ))
                    {
                        continue;
                    }

                    Vec3 blockCenter =
                            Vec3.atCenterOf(cursor);
                    double distance =
                            entityCenter.distanceTo(blockCenter);

                    boolean blockContributed = false;

                    for (BlockTemp blockTemp : blockTemps)
                    {
                        if (!blockTemp.isValid(
                                level,
                                cursor,
                                state
                        ))
                        {
                            continue;
                        }

                        double range =
                                blockTemp.getRange(
                                        entity,
                                        level,
                                        cursor,
                                        state
                                );

                        if (range <= 0.0 || distance > range)
                        {
                            continue;
                        }

                        double source =
                                blockTemp.getTemperature(
                                        level,
                                        entity,
                                        state,
                                        cursor,
                                        distance
                                );

                        /*
                         * M7.12f-b only measures positive/radiant heat.
                         * Ordinary passive cold materials are deliberately not
                         * treated as negative radiation.
                         */
                        if (source <= 0.0)
                        {
                            continue;
                        }

                        if (!state.getFluidState().isEmpty())
                        {
                            source *=
                                    state.getFluidState().getAmount()
                                            / 8.0;
                        }

                        if (blockTemp.fades(
                                entity,
                                level,
                                cursor,
                                state
                        ))
                        {
                            source *= fadeFactor(
                                    distance,
                                    range
                            );
                        }

                        if (source <= 0.0)
                        {
                            continue;
                        }

                        blockContributed = true;

                        if (blockTemp.usesStrongestSource(
                                entity,
                                level,
                                cursor,
                                state
                        ))
                        {
                            strongestRadiantSources.merge(
                                    blockTemp,
                                    source,
                                    Math::max
                            );
                        }
                        else
                        {
                            additiveRadiantLoad += source;
                        }
                    }

                    if (blockContributed)
                    {
                        radiantSourceBlocks++;
                    }
                }
            }
        }

        double radiantLoad = additiveRadiantLoad;
        for (double strongest :
                strongestRadiantSources.values())
        {
            radiantLoad += strongest;
        }

        double skyExposure =
                wideSkySamples > 0
                        ? wideSkyVisible
                                / (double) wideSkySamples
                        : 0.0;

        double waterVolume =
                waterSamples > 0
                        ? waterBlocks
                                / (double) waterSamples
                        : 0.0;

        boolean sheltered =
                meaningfulSky
                        && localSkySamples > 0
                        && localSkyVisible == 0;

        boolean underground =
                meaningfulSky
                        && wideSkySamples > 0
                        && wideSkyVisible == 0;

        return EnvironmentSnapshot.SpatialState.measured(
                sheltered,
                underground,
                skyExposure,
                waterVolume,
                radiantLoad,
                scannedBlocks,
                radiantSourceBlocks
        );
    }

    private static LevelChunk getLoadedChunk(
            ServerLevel level,
            int chunkX,
            int chunkZ,
            Map<Long, LevelChunk> cache
    )
    {
        long key =
                ((long) chunkX << 32)
                        ^ (chunkZ & 0xffffffffL);

        if (cache.containsKey(key))
        {
            return cache.get(key);
        }

        LevelChunk chunk =
                level.getChunkSource()
                        .getChunkNow(chunkX, chunkZ);

        cache.put(key, chunk);
        return chunk;
    }

    private static double fadeFactor(
            double distance,
            double range
    )
    {
        if (range <= 0.0)
        {
            return distance <= 0.5
                    ? 1.0
                    : 0.0;
        }

        if (distance <= 0.5)
        {
            return 1.0;
        }

        double progress =
                (distance - 0.5)
                        / Math.max(
                                0.0001,
                                range - 0.5
                        );

        double remaining =
                1.0 - clamp(
                        progress,
                        0.0,
                        1.0
                );

        return remaining * remaining;
    }

    private static double clamp(
            double value,
            double min,
            double max
    )
    {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }
}
